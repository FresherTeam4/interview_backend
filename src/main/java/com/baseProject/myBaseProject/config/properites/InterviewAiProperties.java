package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.interview.ai")
public record InterviewAiProperties(
        @NotBlank(message = "Thiếu app.interview.ai.model")
        @Size(max = 100, message = "app.interview.ai.model vượt quá 100 ký tự")
        String model,

        @NotBlank(message = "Thiếu app.interview.ai.script-prompt-version")
        @Size(max = 20, message = "app.interview.ai.script-prompt-version vượt quá 20 ký tự")
        String scriptPromptVersion,

        @Positive(message = "app.interview.ai.script-timeout-ms phải là số dương")
        @Max(value = 60000, message = "app.interview.ai.script-timeout-ms tối đa là 60000")
        long scriptTimeoutMs) {
}
