package com.baseProject.myBaseProject.service.impl;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baseProject.myBaseProject.constant.Message;
import com.baseProject.myBaseProject.dto.cv.CvParseOutcome;
import com.baseProject.myBaseProject.dto.cv.CvParsePayload;
import com.baseProject.myBaseProject.exception.CvParseFailedException;
import com.baseProject.myBaseProject.exception.CvParseNotConfiguredException;
import com.baseProject.myBaseProject.service.CvParserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

/**
 * Bóc tách CV bằng AI thông qua Spring AI {@link ChatClient}. Hỗ trợ AI Fallback (Resilience4j).
 *
 * <p>File PDF được gửi trực tiếp cho model dưới dạng multimodal media,
 * kết quả được ánh xạ tự động sang {@link CvParsePayload} qua {@code .entity()}.
 */
@Slf4j
@Service
public class CvParserServiceImpl implements CvParserService {

    // @formatter:off
    private static final String SYSTEM_PROMPT = """
            Bạn là công cụ bóc tách CV chuyên nghiệp. Đọc file CV đính kèm và trả về hồ sơ ứng viên
            dưới dạng JSON theo đúng schema được yêu cầu.

            ## NGUYÊN TẮC CHUNG
            - Chỉ dùng thông tin có thật trong CV, tuyệt đối không suy diễn hay bịa thêm.
            - Giữ nguyên ngôn ngữ gốc của CV cho các trường văn bản (tiếng Việt giữ tiếng Việt,
              tiếng Anh giữ tiếng Anh).
            - Trường nào CV không nói tới thì để null, không đoán.
            - Không lấy thông tin liên hệ cá nhân (email, số điện thoại, địa chỉ).

            ## CÁCH XÁC ĐỊNH seniorityLevel
            Chọn MỘT trong: STUDENT, FRESHER, JUNIOR, MID, SENIOR
            - STUDENT: đang là sinh viên, chưa tốt nghiệp, chỉ có project môn học hoặc thực tập ngắn hạn.
            - FRESHER: mới tốt nghiệp hoặc dưới 1 năm kinh nghiệm thực tế.
            - JUNIOR: 1–2 năm kinh nghiệm làm việc.
            - MID: 3–5 năm kinh nghiệm.
            - SENIOR: trên 5 năm kinh nghiệm hoặc có vai trò lead/architect/manager.
            Nếu CV không ghi rõ và không thể suy ra từ timeline → để null.

            ## CÁCH TÍNH yearsExperience
            - Nếu CV ghi rõ "X năm kinh nghiệm" → dùng giá trị đó.
            - Nếu không ghi rõ → tính từ ngày bắt đầu dự án/công việc chuyên nghiệp đầu tiên
              đến dự án/công việc cuối cùng. Không tính thời gian học, thực tập, hoặc project cá nhân.
            - Nếu không xác định được → null.

            ## CÁCH PHÂN LOẠI SKILL (category)
            Chọn MỘT trong: LANGUAGE, FRAMEWORK, DATABASE, TOOL, SOFT
            - LANGUAGE: ngôn ngữ lập trình (Java, Python, JavaScript, TypeScript, C#, Go, Kotlin...)
            - FRAMEWORK: framework và thư viện chính (Spring Boot, React, Angular, Vue, Django,
              .NET, Node.js, Express, Next.js, Flutter...)
            - DATABASE: hệ quản trị CSDL và data store (MySQL, PostgreSQL, MongoDB, Redis,
              Elasticsearch, Oracle, SQL Server, Firebase...)
            - TOOL: công cụ phát triển và hạ tầng (Git, Docker, Kubernetes, Jenkins, CI/CD,
              AWS, Azure, GCP, Jira, Figma, Postman, Linux...)
            - SOFT: kỹ năng mềm (teamwork, communication, leadership, problem-solving,
              agile/scrum, time management...)
            Mỗi skill chỉ liệt kê MỘT lần, không trùng lặp.

            ## QUY TẮC VỀ NGÀY THÁNG
            - Ghi theo format yyyy-MM-dd (ví dụ: 2024-03-15).
            - Nếu CV chỉ ghi tháng/năm (03/2024 hoặc Mar 2024) → dùng ngày 01: "2024-03-01".
            - Nếu CV chỉ ghi năm (2024) → dùng "2024-01-01".
            - Nếu dự án ghi "đến nay", "present", "hiện tại" → endDate = null.
            - endDate phải >= startDate; nếu ngược thì endDate = null.

            ## QUY TẮC VỀ DỰ ÁN (projects)
            - Sắp xếp theo thứ tự thời gian MỚI NHẤT trước (dự án gần đây nhất ở đầu danh sách).
            - techStack: liệt kê công nghệ dùng trong dự án, phân cách bằng dấu phẩy
              (ví dụ: "Java, Spring Boot, MySQL, Docker"). Nếu CV không liệt kê rõ tech stack
              trong phần dự án nhưng có mô tả → trích xuất từ mô tả.
            - description: tóm tắt 2–4 câu về dự án và phần ứng viên thực hiện, giữ ngôn ngữ gốc.
            - Nếu CV liệt kê kinh nghiệm làm việc (work experience) → cũng coi là project.
            - roleInProject: vai trò của ứng viên (ví dụ: "Backend Developer", "Fullstack Developer",
              "Team Lead"). Nếu không ghi rõ → null.

            ## QUY TẮC VỀ HỌC VẤN (educations)
            - endYear >= startYear; nếu ngược thì endYear = null.
            - Nếu đang học → endYear = năm dự kiến tốt nghiệp (nếu CV ghi) hoặc null.
            - degree: bằng cấp (ví dụ: "Cử nhân", "Kỹ sư", "Bachelor", "Master").
            - fieldOfStudy: chuyên ngành (ví dụ: "Công nghệ thông tin", "Computer Science").

            ## headline
            - Một dòng tóm tắt chuyên môn, ví dụ: "Java Backend Developer, 2 năm kinh nghiệm".
            - Nếu CV có dòng summary/objective → dùng làm headline.
            - Nếu không có → tự tạo từ vị trí + số năm kinh nghiệm + công nghệ chính.

            ## targetPosition
            - Vị trí ứng viên đang ứng tuyển hoặc mong muốn, như ghi trong CV.
            - Nếu CV không ghi rõ → null.
            """;
    // @formatter:on

    private static final String USER_PROMPT =
            "Bóc tách CV đính kèm thành hồ sơ ứng viên theo đúng cấu trúc JSON yêu cầu. "
            + "Field nào CV không có thì trả về null, tuyệt đối không bịa.";

    private static final String DUMMY_KEY_PREFIX = "dummy-";

    private final ChatClient primaryClient;
    private final ChatClient fallbackClient;
    private final ObjectMapper objectMapper;
    private final String primaryModelName;
    private final String fallbackModelName;
    private final boolean aiConfigured;

    public CvParserServiceImpl(
            @Qualifier("primaryChatClient") ChatClient primaryClient,
            @Qualifier("fallbackChatClient") ChatClient fallbackClient,
            ObjectMapper objectMapper,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o}") String primaryModelName,
            @Value("${spring.ai.google.genai.chat.options.model:gemini-3.5-flash-lite}") String fallbackModelName,
            @Value("${spring.ai.openai.api-key:}") String primaryApiKey,
            @Value("${spring.ai.google.genai.api-key:}") String fallbackApiKey) {
        this.primaryClient = primaryClient;
        this.fallbackClient = fallbackClient;
        this.objectMapper = objectMapper;
        this.primaryModelName = primaryModelName;
        this.fallbackModelName = fallbackModelName;
        this.aiConfigured = isRealKey(primaryApiKey) || isRealKey(fallbackApiKey);
    }

    /** Key rỗng hoặc key mẫu {@code dummy-...} đều coi như chưa cấu hình AI. */
    private static boolean isRealKey(String apiKey) {
        return StringUtils.hasText(apiKey) && !apiKey.startsWith(DUMMY_KEY_PREFIX);
    }

    @Override
    @Retry(name = "cvParseRetry", fallbackMethod = "parseFallback")
    public CvParseOutcome parse(byte[] pdfBytes) {
        if (!aiConfigured) {
            throw new CvParseNotConfiguredException();
        }
        log.info("Gọi Primary AI Model ({}) để bóc tách CV...", primaryModelName);
        return executeParse(primaryClient, pdfBytes, primaryModelName);
    }

    /**
     * Hàm Fallback: được tự động gọi nếu Primary AI gặp lỗi và sau khi đã hết số lần Retry.
     * Signature của hàm fallback phải giống hệt hàm gốc + thêm tham số Throwable.
     */
    public CvParseOutcome parseFallback(byte[] pdfBytes, Throwable t) {
        // Chưa cấu hình khóa AI thì gọi model nào cũng vô nghĩa; ném lại để client nhận 503
        // đúng nguyên nhân thay vì lỗi "bóc tách thất bại" chung chung.
        if (t instanceof CvParseNotConfiguredException ex) {
            throw ex;
        }
        log.warn("Primary AI ({}) thất bại do: {}. Chuyển sang Fallback AI Model ({})...",
                primaryModelName, t.getMessage(), fallbackModelName);
        return executeParse(fallbackClient, pdfBytes, fallbackModelName);
    }

    private CvParseOutcome executeParse(ChatClient client, byte[] pdfBytes, String modelName) {
        Media pdfMedia = Media.builder()
                .name("cv.pdf")
                .data(pdfBytes)
                .mimeType(org.springframework.util.MimeType.valueOf("application/pdf"))
                .build();

        long startedAt = System.currentTimeMillis();
        try {
            CvParsePayload payload = client.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userSpec -> userSpec
                            .text(USER_PROMPT)
                            .media(pdfMedia))
                    .call()
                    .entity(CvParsePayload.class);

            int durationMs = (int) (System.currentTimeMillis() - startedAt);

            if (payload == null) {
                throw new CvParseFailedException(Message.CV_PARSE_EMPTY_RESULT);
            }

            return new CvParseOutcome(
                    payload,
                    toJson(payload),
                    modelName,
                    durationMs,
                    null
            );
        } catch (CvParseFailedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Bóc tách CV thất bại với model {}", modelName, ex);
            Throwable root = ex;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            throw new CvParseFailedException(Message.CV_PARSE_FAILED + " - Lỗi chi tiết: " + root.getMessage());
        }
    }

    private String toJson(CvParsePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new CvParseFailedException(Message.CV_PARSE_FAILED);
        }
    }
}
