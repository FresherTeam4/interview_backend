package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.job-description")
public record JobDescriptionProperties(
        @Positive long maxFileSizeBytes,
        @Positive int maxPages,
        @Positive int maxPerUser,
        @Positive int maxTextCharacters) {
}
