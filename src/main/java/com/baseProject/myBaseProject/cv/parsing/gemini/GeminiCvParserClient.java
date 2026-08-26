package com.baseProject.myBaseProject.cv.parsing.gemini;

import com.baseProject.myBaseProject.config.properites.AiProperties;
import com.baseProject.myBaseProject.dto.ai.CvParsedPayload;
import com.baseProject.myBaseProject.exception.CvParseFailedException;
import com.baseProject.myBaseProject.cv.parsing.CvParserClient;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.errors.ServerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiCvParserClient implements CvParserClient {

    private static final int LOGGED_BODY_LIMIT = 1000;
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final AiProperties aiProperties;
    private final JsonMapper jsonMapper;
    private final ObjectProvider<GoogleGenAiChatModel> chatModelProvider;
    private final String prompt;
    private final String responseSchema;

    /**
     * Prompt và schema được đọc đúng một lần. Schema cũng được parse ngay để file sai làm app
     * fail lúc khởi động, không chờ đến CV đầu tiên mới phát hiện.
     */
    public GeminiCvParserClient(AiProperties aiProperties,
                                JsonMapper jsonMapper,
                                ResourceLoader resourceLoader,
                                ObjectProvider<GoogleGenAiChatModel> chatModelProvider) {
        this.aiProperties = aiProperties;
        this.jsonMapper = jsonMapper;
        this.chatModelProvider = chatModelProvider;

        String version = aiProperties.schemaVersion();
        this.prompt = readTextResource(resourceLoader,
                "classpath:ai/cv-parse-prompt-%s.txt".formatted(version));
        String schemaContract = readTextResource(resourceLoader,
                "classpath:ai/cv-parse-schema-%s.json".formatted(version));
        this.responseSchema = toSpringAiSchema(schemaContract);
    }

    @Override
    public ParseOutcome parse(byte[] pdfContent) {
        if (!aiProperties.hasApiKey()) {
            throw CvParseFailedException.noApiKey();
        }

        Prompt request = buildRequest(pdfContent);
        log.debug("Gửi {} KB PDF tới Gemini qua Spring AI (model {})",
                pdfContent.length / 1024, aiProperties.model());

        long startedAtNanos = System.nanoTime();
        ChatResponse response = call(request);
        int durationMs = (int) Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();

        return readOutcome(response, durationMs);
    }

    /** Spring AI và Google GenAI SDK tự mã hóa byte PDF sang dạng inline phù hợp với Gemini. */
    private Prompt buildRequest(byte[] pdfContent) {
        Media pdf = Media.builder()
                .mimeType(MediaType.APPLICATION_PDF)
                .data(pdfContent)
                .name("candidate-cv")
                .build();

        UserMessage userMessage = UserMessage.builder()
                .text(prompt)
                .media(pdf)
                .build();

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(aiProperties.model())
                .outputSchema(responseSchema)
                .build();

        return new Prompt(userMessage, options);
    }

    private ChatResponse call(Prompt request) {
        try {
            return chatModelProvider.getObject().call(request);
        } catch (RuntimeException e) {
            ClientException clientError = findCause(e, ClientException.class);
            if (clientError != null) {
                if (clientError.code() == 429) {
                    log.warn("Gemini giới hạn tần suất khi bóc tách CV");
                    throw CvParseFailedException.aiUnavailable("mã HTTP 429", e);
                }
                log.error("Gemini từ chối request bóc tách CV với mã {}: {}",
                        clientError.code(), truncate(clientError.message()));
                throw CvParseFailedException.unexpected(e);
            }

            ServerException serverError = findCause(e, ServerException.class);
            if (serverError != null) {
                log.warn("Gemini trả mã {} khi bóc tách CV", serverError.code());
                throw CvParseFailedException.aiUnavailable(
                        "mã HTTP " + serverError.code(), e);
            }

            ApiException apiError = findCause(e, ApiException.class);
            if (apiError != null) {
                log.warn("Gemini trả mã ngoài dự kiến {} khi bóc tách CV", apiError.code());
                throw CvParseFailedException.aiUnavailable(
                        "mã HTTP " + apiError.code(), e);
            }

            if (hasTimeoutCause(e)) {
                throw CvParseFailedException.timeout(e);
            }
            if (findCause(e, GenAiIOException.class) != null) {
                throw CvParseFailedException.aiUnavailable(
                        "không nối được tới Gemini", e);
            }

            throw CvParseFailedException.unexpected(e);
        }
    }

    private ParseOutcome readOutcome(ChatResponse response, int durationMs) {
        if (response == null) {
            throw CvParseFailedException.badResponse("phản hồi rỗng");
        }

        Generation result = response.getResult();
        String modelOutput = result == null ? null : result.getOutput().getText();
        if (modelOutput == null || modelOutput.isBlank()) {
            throw CvParseFailedException.badResponse("không có nội dung trả về");
        }

        String rawJson = stripCodeFences(modelOutput);
        CvParsedPayload payload;
        try {
            payload = jsonMapper.readerFor(CvParsedPayload.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawJson);
        } catch (JacksonException e) {
            log.error("Kết quả Gemini không khớp schema {}: {}",
                    aiProperties.schemaVersion(), e.getOriginalMessage());
            throw CvParseFailedException.badResponse(
                    "model output không khớp schema " + aiProperties.schemaVersion(), e);
        }

        String responseModel = response.getMetadata().getModel();
        Integer totalTokens = response.getMetadata().getUsage().getTotalTokens();

        return new ParseOutcome(
                rawJson,
                payload,
                responseModel == null || responseModel.isBlank()
                        ? aiProperties.model() : responseModel,
                aiProperties.schemaVersion(),
                totalTokens == null || totalTokens == 0 ? null : totalTokens,
                durationMs);
    }

    // remove markdown code
    private static String stripCodeFences(String modelOutput) {
        String trimmed = modelOutput.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstLineEnd = trimmed.indexOf('\n');
        String withoutFirstLine = firstLineEnd < 0 ? "" : trimmed.substring(firstLineEnd + 1);
        String withoutClosing = withoutFirstLine.endsWith("```")
                ? withoutFirstLine.substring(0, withoutFirstLine.length() - 3)
                : withoutFirstLine;
        return withoutClosing.strip();
    }

    private static boolean hasTimeoutCause(Throwable throwable) {
        return findCause(throwable, InterruptedIOException.class) != null;
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
            if (cause == cause.getCause()) {
                break;
            }
        }
        return null;
    }

    private String toSpringAiSchema(String schemaContract) {
        Map<String, Object> schema = jsonMapper.readValue(schemaContract, JSON_OBJECT);
        normalizeSchema(schema);
        return jsonMapper.writeValueAsString(schema);
    }

    @SuppressWarnings("unchecked")
    private static void normalizeSchema(Map<String, Object> schema) {
        schema.remove("additionalProperties");

        Object type = schema.get("type");
        if (type instanceof List<?> types) {
            List<Object> nonNullTypes = new ArrayList<>();
            boolean nullable = false;
            for (Object value : types) {
                if ("null".equals(value)) {
                    nullable = true;
                } else {
                    nonNullTypes.add(value);
                }
            }
            if (nonNullTypes.size() == 1) {
                schema.put("type", nonNullTypes.get(0));
            } else {
                schema.put("type", nonNullTypes);
            }
            if (nullable) {
                schema.put("nullable", true);
            }
        }

        Object enumValues = schema.get("enum");
        if (enumValues instanceof List<?> values && values.contains(null)) {
            List<Object> withoutNull = new ArrayList<>();
            for (Object value : values) {
                if (value != null) {
                    withoutNull.add(value);
                }
            }
            if (withoutNull.isEmpty()) {
                schema.remove("enum");
            } else {
                schema.put("enum", withoutNull);
            }
        }

        Object properties = schema.get("properties");
        if (properties instanceof Map<?, ?> propertyMap) {
            for (Object child : propertyMap.values()) {
                if (child instanceof Map<?, ?> childSchema) {
                    normalizeSchema((Map<String, Object>) childSchema);
                }
            }
        }

        Object items = schema.get("items");
        if (items instanceof Map<?, ?> itemSchema) {
            normalizeSchema((Map<String, Object>) itemSchema);
        }

        for (String combiner : List.of("anyOf", "allOf", "oneOf")) {
            Object branches = schema.get(combiner);
            if (branches instanceof List<?> branchList) {
                for (Object branch : branchList) {
                    if (branch instanceof Map<?, ?> branchSchema) {
                        normalizeSchema((Map<String, Object>) branchSchema);
                    }
                }
            }
        }
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

    private static String truncate(String body) {
        if (body == null || body.isBlank()) {
            return "<rỗng>";
        }
        return body.length() <= LOGGED_BODY_LIMIT
                ? body
                : body.substring(0, LOGGED_BODY_LIMIT) + "... (đã cắt)";
    }
}
