package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.AiProperties;
import com.baseProject.myBaseProject.dto.ai.CvParsedPayload;
import com.baseProject.myBaseProject.exception.CvParseFailedException;
import com.baseProject.myBaseProject.service.CvParserClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Bóc tách CV bằng Gemini Interactions API.
 *
 * <p>Gọi bằng {@code RestClient} thay vì SDK của Google: cả tính năng chỉ cần đúng một endpoint,
 * thêm một SDK là thêm một nhánh phụ thuộc phải nâng cấp theo (mục 5.2 của
 * {@code docs/cv-profile-api.md}).
 *
 * <p><strong>Tự serialize request và nhận response ở dạng byte.</strong> Nhờ vậy client không phụ
 * thuộc message converter nào, và khi Gemini trả về thứ ngoài dự đoán thì vẫn còn nguyên văn bản
 * thô để điều tra — điều đáng giá ở đây, vì có hai chi tiết trong hợp đồng API chưa kiểm được
 * bằng {@code curl} (mục 5.3): đường dẫn {@code /v1beta} hay {@code /v1beta2}, và
 * {@code response_format} là object hay array. Cả hai đổi được bằng cấu hình hoặc sửa một record,
 * không phải viết lại client.
 *
 * <p>Tự mã hóa UTF-8 hai chiều thay vì để {@code String} qua message converter, vì cả prompt gửi
 * đi lẫn CV nhận về đều có tiếng Việt: JSON theo đặc tả luôn là UTF-8, nói thẳng ra như vậy thì
 * không phụ thuộc charset mặc định của bên nào.
 *
 * <p>Prompt và JSON Schema nằm trong {@code src/main/resources/ai/}, tên file mang
 * {@code app.ai.schema-version}. Đổi cách bóc tách là thêm cặp file {@code v2} rồi sửa một dòng
 * cấu hình — đúng nghĩa "hợp đồng" ở mục 5.4, và những hàng {@code cv_parse_results} cũ vẫn đọc
 * được vì cột {@code schema_version} nói chúng theo bản nào.
 */
@Slf4j
@Service
public class GeminiCvParserClient implements CvParserClient {

    private static final String INTERACTIONS_PATH = "/interactions";
    private static final String API_KEY_HEADER = "x-goog-api-key";
    private static final String MODEL_OUTPUT_STEP = "model_output";
    private static final String STATUS_COMPLETED = "completed";

    /** Nối được TCP thì rất nhanh; chờ lâu ở đây chỉ là DNS/mạng sai, không phải AI nghĩ lâu. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** Cắt bớt khi ghi log: phản hồi lỗi của Google có thể dài, log không cần trọn vẹn. */
    private static final int LOGGED_BODY_LIMIT = 1000;

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final AiProperties aiProperties;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;
    private final String prompt;
    private final Map<String, Object> responseSchema;

    /**
     * Đọc prompt và schema <em>một lần lúc khởi động</em>.
     *
     * <p>Cố ý không đọc lại ở mỗi lần parse: file thiếu hoặc schema sai cú pháp JSON thì app chết
     * ngay lúc bật, chứ không phải đến CV đầu tiên của người dùng mới lộ ra.
     *
     * <p><strong>Đừng thêm {@code maxItems} vào file schema.</strong> Gemini trả
     * {@code 400 invalid_request} cho cả schema — đã thử: bỏ hết {@code maxItems} thì 200, để
     * {@code maxItems} ở cả ba mảng (dù đã hạ xuống 20) thì 400, nên đây là hạn mức độ phức tạp
     * tính trên toàn schema chứ không phải trần của từng mảng. Giới hạn số lượng đã nằm ở hai chỗ
     * khác: prompt nói bằng lời (tối đa 20 học vấn, 100 kỹ năng, 50 dự án) và {@code ProfileMapper}
     * kẹp lại khi ghi DB, nên mô hình trả dư cũng không vỡ gì.
     */
    public GeminiCvParserClient(AiProperties aiProperties, JsonMapper jsonMapper,
                                ResourceLoader resourceLoader) {
        this.aiProperties = aiProperties;
        this.jsonMapper = jsonMapper;
        this.restClient = buildRestClient(aiProperties);

        String version = aiProperties.schemaVersion();
        this.prompt = readTextResource(resourceLoader,
                "classpath:ai/cv-parse-prompt-%s.txt".formatted(version));
        this.responseSchema = jsonMapper.readValue(readTextResource(resourceLoader,
                "classpath:ai/cv-parse-schema-%s.json".formatted(version)), JSON_OBJECT);
    }

    /**
     * {@code RestClient} riêng, không dùng bean {@code RestClient.Builder} auto-config.
     *
     * <p>Vì hai timeout ở đây là của riêng việc gọi Gemini: đọc chờ tới
     * {@code app.ai.timeout-ms} (25 giây — mô hình cần chừng đó để đọc xong một CV), con số mà
     * không request HTTP nào khác trong app nên chịu.
     */
    private static RestClient buildRestClient(AiProperties aiProperties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(Duration.ofMillis(aiProperties.timeoutMs()));

        return RestClient.builder()
                .baseUrl(aiProperties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private static String readTextResource(ResourceLoader resourceLoader, String location) {
        Resource resource = resourceLoader.getResource(location);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Không đọc được %s — kiểm tra app.ai.schema-version".formatted(location), e);
        }
    }

    @Override
    public ParseOutcome parse(byte[] pdfContent) {
        // Chặn trước khi gọi mạng: thiếu khóa thì Gemini trả 400/403 và ta phải đoán lý do.
        if (!aiProperties.hasApiKey()) {
            throw CvParseFailedException.noApiKey();
        }

        byte[] requestBody = serialize(buildRequest(pdfContent));
        log.debug("Gửi {} KB tới Gemini (model {})", requestBody.length / 1024, aiProperties.model());

        // Đo đúng thời gian nằm chờ mạng, bằng nanoTime chứ không phải Clock: đây là khoảng thời
        // gian đã trôi qua, không phải mốc thời gian — Clock có thể bị NTP kéo lùi giữa hai lần đọc.
        long startedAtNanos = System.nanoTime();
        String responseBody = call(requestBody);
        int durationMs = (int) Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();

        return readOutcome(responseBody, durationMs);
    }

    private byte[] serialize(ParseRequest request) {
        try {
            return jsonMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
        } catch (JacksonException e) {
            // Gần như không thể xảy ra: request chỉ gồm record với String/Map.
            throw CvParseFailedException.unexpected(e);
        }
    }

    /**
     * Dựng thân request theo mục 5.2.
     *
     * <p>Gửi thẳng file PDF dạng base64, không rút chữ trước: layout hai cột và bảng biểu của CV
     * mà rút thành text phẳng thì mất thứ tự đọc, mà thứ tự đọc là thứ mô hình cần để biết mục nào
     * thuộc mục nào (quyết định số 6, mục 13).
     *
     * <p>Trong body <strong>không có gì ngoài file CV</strong>: không email, không {@code userId}
     * (mục 10). Prompt là chuỗi cố định theo {@code schema_version}, không ghép dữ liệu người dùng.
     */
    private ParseRequest buildRequest(byte[] pdfContent) {
        String base64Pdf = Base64.getEncoder().encodeToString(pdfContent);

        return new ParseRequest(
                aiProperties.model(),
                List.of(new DocumentInput("document", base64Pdf, MediaType.APPLICATION_PDF_VALUE),
                        new TextInput("text", prompt)),
                new ResponseFormat("text", MediaType.APPLICATION_JSON_VALUE, responseSchema));
    }

    /**
     * Một lời gọi HTTP, và cách phân loại thất bại.
     *
     * <p>Ba nhánh khác nhau vì chúng đòi hỏi ba hành động khác nhau:
     * <ul>
     *   <li><b>4xx</b> — request của <em>mình</em> sai (sai shape, sai model, khóa hết hạn). Người
     *       dùng bấm lại bao nhiêu lần cũng vẫn 4xx, nên ghi log mức error kèm nguyên văn thân
     *       phản hồi: đó chính là chỗ Google nói mình sai ở đâu, và cũng là chỗ trả lời hai câu
     *       hỏi còn treo ở mục 5.3.</li>
     *   <li><b>5xx/429</b> — phía họ. Thử lại sau là hợp lý.</li>
     *   <li><b>Không nối được / quá hạn đọc</b> — {@code ResourceAccessException} bọc
     *       {@code IOException}; chỉ khi nguyên nhân gốc là {@code HttpTimeoutException} thì mới
     *       gọi là timeout, còn lại là mạng.</li>
     * </ul>
     */
    private String call(byte[] requestBody) {
        try {
            byte[] responseBody = restClient.post()
                    .uri(INTERACTIONS_PATH)
                    .header(API_KEY_HEADER, aiProperties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(byte[].class);

            return responseBody == null ? "" : new String(responseBody, StandardCharsets.UTF_8);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.error("Gemini từ chối request bóc tách CV với mã {}. Thân phản hồi: {}",
                        e.getStatusCode(), truncate(e.getResponseBodyAsString()));
                throw CvParseFailedException.unexpected(e);
            }
            log.warn("Gemini trả mã {} khi bóc tách CV", e.getStatusCode());
            throw CvParseFailedException.aiUnavailable("mã HTTP " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            if (hasTimeoutCause(e)) {
                throw CvParseFailedException.timeout(e);
            }
            throw CvParseFailedException.aiUnavailable("không nối được tới " + aiProperties.baseUrl(), e);
        }
    }

    private static boolean hasTimeoutCause(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Đọc phản hồi, phòng thủ đúng ở ba chỗ mục 5.3 đã cảnh báo: lọc {@code steps[]} theo
     * {@code type}, kiểm {@code status}, và parse hai lần vì {@code text} là chuỗi chứa JSON.
     *
     * <p><strong>Không ghi nội dung hồ sơ vào log, kể cả khi lỗi</strong> — đó là dữ liệu cá nhân
     * của người thật (mục 10). Chỗ cần điều tra thì ghi <em>hình dạng</em> phản hồi
     * ({@link #describeShape(String)}) hoặc câu báo lỗi của Jackson: đủ để biết sai ở đâu mà không
     * đổ CV của người khác vào file log.
     */
    private ParseOutcome readOutcome(String responseBody, int durationMs) {
        if (responseBody.isBlank()) {
            throw CvParseFailedException.badResponse("thân phản hồi rỗng");
        }

        GeminiResponse response;
        try {
            response = jsonMapper.readValue(responseBody, GeminiResponse.class);
        } catch (JacksonException e) {
            // Không đọc được thành JSON thì đây không phải hồ sơ, mà là trang lỗi của proxy hay
            // gateway — ghi nguyên văn (đã cắt bớt) là cách duy nhất biết ai đã trả lời mình.
            log.error("Phản hồi Gemini không phải JSON: {}", truncate(responseBody));
            throw CvParseFailedException.badResponse("không phải JSON hợp lệ", e);
        }

        if (!STATUS_COMPLETED.equals(response.status())) {
            log.warn("Gemini trả status '{}' thay vì '{}' ({})",
                    response.status(), STATUS_COMPLETED, describeShape(responseBody));
            throw CvParseFailedException.badResponse("status = " + response.status());
        }

        String rawJson = stripCodeFences(extractModelOutput(response, responseBody));
        CvParsedPayload payload;
        try {
            // Bỏ qua trường lạ thay vì đổ cả lần bóc tách: mô hình thêm một khóa ngoài schema là
            // chuyện có thể xảy ra, và phần dữ liệu mình cần thì vẫn nguyên vẹn.
            payload = jsonMapper.readerFor(CvParsedPayload.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawJson);
        } catch (JacksonException e) {
            log.error("model_output không khớp schema {}: {}",
                    aiProperties.schemaVersion(), e.getOriginalMessage());
            throw CvParseFailedException.badResponse(
                    "model_output không khớp schema " + aiProperties.schemaVersion(), e);
        }

        return new ParseOutcome(rawJson, payload,
                response.model() == null ? aiProperties.model() : response.model(),
                aiProperties.schemaVersion(),
                response.usage() == null ? null : response.usage().totalTokens(),
                durationMs);
    }

    /**
     * Lấy chuỗi JSON hồ sơ ra khỏi phản hồi.
     *
     * <p>Lọc theo {@code type} chứ không lấy {@code steps[0]}: trước {@code model_output} còn có
     * {@code thought} (Gemini 3.x suy nghĩ trước khi trả lời), và có thể có {@code user_input}.
     *
     * <p>Chấp nhận cả {@code steps} lẫn {@code output} vì tên mảng là chi tiết chưa kiểm được của
     * mục 5.3: nhận cả hai thì việc Google đổi tên giữa các bản API không làm chết tính năng.
     */
    private String extractModelOutput(GeminiResponse response, String responseBody) {
        List<GeminiStep> steps = response.steps() == null ? response.output() : response.steps();
        if (steps == null || steps.isEmpty()) {
            log.error("Phản hồi Gemini không có 'steps' lẫn 'output' ({})", describeShape(responseBody));
            throw CvParseFailedException.badResponse("thiếu cả steps và output");
        }

        for (GeminiStep step : steps) {
            if (!MODEL_OUTPUT_STEP.equals(step.type()) || step.content() == null) {
                continue;
            }
            for (GeminiContent content : step.content()) {
                if (content.text() != null && !content.text().isBlank()) {
                    return content.text();
                }
            }
        }

        log.error("Không có step '{}' nào mang text ({})", MODEL_OUTPUT_STEP, describeShape(responseBody));
        throw CvParseFailedException.badResponse("không tìm thấy " + MODEL_OUTPUT_STEP);
    }

    /**
     * Bỏ khối markdown bọc ngoài, nếu mô hình vẫn bọc dù prompt đã dặn đừng.
     *
     * <p>Mười dòng này rẻ hơn hẳn cái giá của việc bỏ qua: {@code cv_parse_results.raw_json} là
     * cột kiểu {@code JSON} của MySQL, ba dấu backtick lọt vào là INSERT đổ và mất trắng một lần
     * bóc tách đã trả tiền API.
     */
    private static String stripCodeFences(String modelOutput) {
        String trimmed = modelOutput.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        // Dòng đầu là ``` hoặc ```json — bỏ cả dòng, rồi bỏ ``` đóng ở cuối nếu có.
        int firstLineEnd = trimmed.indexOf('\n');
        String withoutFirstLine = firstLineEnd < 0 ? "" : trimmed.substring(firstLineEnd + 1);
        String withoutClosing = withoutFirstLine.endsWith("```")
                ? withoutFirstLine.substring(0, withoutFirstLine.length() - 3)
                : withoutFirstLine;

        return withoutClosing.strip();
    }

    /**
     * Mô tả hình dạng phản hồi mà không tiết lộ giá trị: chỉ tên khóa ở cấp ngoài cùng và độ dài.
     *
     * <p>Đúng thứ cần để trả lời "Gemini trả về cấu trúc gì" — hai điều còn treo ở mục 5.3 — mà
     * không ghi một chữ nào của CV vào log.
     */
    private String describeShape(String responseBody) {
        try {
            return "các khóa cấp ngoài: %s, dài %d ký tự".formatted(
                    jsonMapper.readValue(responseBody, JSON_OBJECT).keySet(), responseBody.length());
        } catch (JacksonException e) {
            return "không phải JSON object, dài %d ký tự".formatted(responseBody.length());
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "<rỗng>";
        }
        return body.length() <= LOGGED_BODY_LIMIT
                ? body
                : body.substring(0, LOGGED_BODY_LIMIT) + "... (đã cắt)";
    }

    // ---- Hình dạng JSON của Gemini Interactions API ----
    // Để record lồng trong chính client, không đưa vào package dto: đây không phải hợp đồng của
    // app với ai, chỉ là hình dạng dữ liệu của một nhà cung cấp. Đổi nhà cung cấp thì xóa cả
    // class này mà không chỗ nào khác phải sửa theo.

    /** Thân request theo mục 5.2. */
    record ParseRequest(
            String model,
            List<Object> input,
            @JsonProperty("response_format") ResponseFormat responseFormat
    ) {
    }

    /**
     * Phần cưỡng chế định dạng đầu ra.
     *
     * <p>Đây là thứ biến "trả JSON theo schema cố định" thành ràng buộc do Gemini thực thi, chứ
     * không phải một lời nhắc trong prompt rồi cầu mong (mục 5.2). Vẫn parse phòng thủ ở phía mình.
     */
    record ResponseFormat(
            String type,
            @JsonProperty("mime_type") String mimeType,
            Map<String, Object> schema
    ) {
    }

    /** File PDF gửi kèm; {@code data} là base64 của nguyên file. */
    record DocumentInput(
            String type,
            String data,
            @JsonProperty("mime_type") String mimeType
    ) {
    }

    /** Prompt cố định theo {@code schema_version}. */
    record TextInput(String type, String text) {
    }

    /**
     * Phản hồi ở cấp ngoài cùng.
     *
     * <p>{@code steps} và {@code output} là hai tên cho cùng một thứ, đúng một trong hai sẽ có giá
     * trị — xem {@link #extractModelOutput(GeminiResponse, String)}.
     *
     * <p>{@code @JsonIgnoreProperties} ở tất cả các record dưới đây là bắt buộc chứ không phải cho
     * gọn: Gemini còn trả {@code id}, {@code object}, {@code signature}... và sẽ còn thêm nữa.
     * Thiếu nó thì mỗi lần Google bổ sung một khóa là mọi lần bóc tách đều thất bại.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(
            String status,
            String model,
            GeminiUsage usage,
            List<GeminiStep> steps,
            List<GeminiStep> output
    ) {
    }

    /** Chỉ lấy tổng token; {@code total_input_tokens}/{@code total_output_tokens} không cần. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiUsage(@JsonProperty("total_tokens") Integer totalTokens) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiStep(String type, List<GeminiContent> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiContent(String type, String text) {
    }
}
