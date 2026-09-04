package com.baseProject.myBaseProject.dto.interview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record VoiceAttemptUploadRequest(
        @NotNull(message = "promptTurnId là bắt buộc")
        @Positive(message = "promptTurnId phải là số dương")
        Long promptTurnId,

        @NotBlank(message = "clientAttemptId là bắt buộc")
        @Size(max = 64, message = "clientAttemptId tối đa 64 ký tự")
        String clientAttemptId,

        @NotNull(message = "durationMs là bắt buộc")
        Integer durationMs,

        @NotNull(message = "expectedVersion là bắt buộc")
        @PositiveOrZero(message = "expectedVersion không được âm")
        Long expectedVersion) {
}
