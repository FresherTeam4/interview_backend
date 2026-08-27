package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.interview")
public record InterviewProperties(
        boolean enabled,
        @Min(1) @Max(3600) long processingLeaseSeconds,
        @Min(1) @Max(100) int maxActivePerUser,
        @Min(1) @Max(100000) int maxAnswerChars) {

    public Duration processingLease() {
        return Duration.ofSeconds(processingLeaseSeconds);
    }
}
