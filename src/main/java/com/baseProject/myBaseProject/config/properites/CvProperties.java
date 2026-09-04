package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cv")
public record CvProperties(
        @Positive(message = "app.cv.max-file-size-bytes must be positive")
        long maxFileSizeBytes,

        @Positive(message = "app.cv.max-pages must be positive")
        int maxPages,

        @Positive(message = "app.cv.max-per-user must be positive")
        int maxPerUser
) {
}
