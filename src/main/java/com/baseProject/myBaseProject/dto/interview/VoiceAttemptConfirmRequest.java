package com.baseProject.myBaseProject.dto.interview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record VoiceAttemptConfirmRequest(
        @NotBlank(message = "clientTurnId là bắt buộc")
        @Size(max = 64, message = "clientTurnId tối đa 64 ký tự")
        String clientTurnId,

        @NotNull(message = "expectedSessionVersion là bắt buộc")
        @PositiveOrZero(message = "expectedSessionVersion không được âm")
        Long expectedSessionVersion,

        @NotNull(message = "expectedAttemptVersion là bắt buộc")
        @PositiveOrZero(message = "expectedAttemptVersion không được âm")
        Long expectedAttemptVersion) {
}
