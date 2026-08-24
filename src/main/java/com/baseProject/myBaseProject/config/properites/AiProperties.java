package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình lần gọi Gemini để bóc tách CV.
 *
 * <p>{@code apiKey} cố tình không {@code @NotBlank}: thiếu key thì app vẫn khởi động được
 * (chạy test, làm tính năng khác), và lần bóc tách đầu tiên sẽ trả về FAILED kèm lý do rõ
 * ràng — đúng như yêu cầu "báo lỗi rõ khi không xử lý được".
 */
@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        @NotBlank(message = "Thiếu app.ai.base-url")
        String baseUrl,

        /** Không để trong repo. Rỗng là hợp lệ, xem javadoc của lớp. */
        String apiKey,

        /** Ghi vào {@code cv_parse_results.model_name} — cột VARCHAR(100). */
        @NotBlank(message = "Thiếu app.ai.model")
        @Size(max = 100, message = "app.ai.model dài quá cột model_name VARCHAR(100)")
        String model,

        /** Ghi vào {@code cv_parse_results.schema_version} — cột VARCHAR(20). */
        @NotBlank(message = "Thiếu app.ai.schema-version")
        @Size(max = 20, message = "app.ai.schema-version dài quá cột schema_version VARCHAR(20)")
        String schemaVersion,

        /** Trần thời gian một lần gọi. Yêu cầu nghiệp vụ là cả luồng dưới 30 giây. */
        @Positive(message = "app.ai.timeout-ms phải là số dương")
        long timeoutMs
) {
    /** Chỗ duy nhất được phép hỏi "đã có key chưa" — tránh rải kiểm tra rỗng khắp nơi. */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
