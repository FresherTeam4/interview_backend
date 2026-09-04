package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.voice")
public record VoiceProperties(
        @Positive @Max(15728640) long maxFileSizeBytes,
        @Positive @Max(300000) int maxDurationMs,
        @Min(1) @Max(3650) int audioRetentionDays,
        boolean transcriptionEnabled,
        @NotBlank @Size(max = 100) String sttModel,
        @NotBlank @Size(max = 20) String sttPromptVersion,
        @Positive @Max(60000) long sttTimeoutMs,
        @Min(5) @Max(3600) long sttProcessingLeaseSeconds) {
}
