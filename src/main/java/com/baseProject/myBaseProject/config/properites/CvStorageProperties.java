package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Nơi lưu file CV gốc và giới hạn dung lượng cho phép upload. */
@Validated
@ConfigurationProperties(prefix = "app.cv.storage")
public record CvStorageProperties(
        @NotBlank(message = "Thiếu cấu hình app.cv.storage.dir")
        String dir,

        @Positive
        long maxFileSizeBytes
) { }
