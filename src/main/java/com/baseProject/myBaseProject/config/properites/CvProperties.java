package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Hạn mức của tính năng CV. Ba con số này là toàn bộ chính sách chặn upload:
 * to quá, dài quá, nhiều quá.
 *
 * <p>{@code maxFileSizeBytes} phải nhỏ hơn {@code spring.servlet.multipart.max-file-size},
 * nếu không servlet sẽ chặn trước và người dùng nhận lỗi thô thay vì thông báo của app.
 */
@Validated
@ConfigurationProperties(prefix = "app.cv")
public record CvProperties(
        @Positive(message = "app.cv.max-file-size-bytes phải là số dương")
        long maxFileSizeBytes,

        @Positive(message = "app.cv.max-pages phải là số dương")
        int maxPages,

        @Positive(message = "app.cv.max-per-user phải là số dương")
        int maxPerUser
) {
}
