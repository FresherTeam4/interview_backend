package com.baseProject.myBaseProject.dto.interview;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SubmitTextAnswerRequest(
        @NotNull(message = "promptTurnId là bắt buộc")
        @Positive(message = "promptTurnId phải là số dương")
        Long promptTurnId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 10000)
        String content,

        @NotBlank(message = "clientTurnId là bắt buộc")
        @Size(max = 64, message = "clientTurnId tối đa 64 ký tự")
        String clientTurnId,

        @NotNull(message = "expectedVersion là bắt buộc")
        @PositiveOrZero(message = "expectedVersion không được âm")
        Long expectedVersion) {
}
