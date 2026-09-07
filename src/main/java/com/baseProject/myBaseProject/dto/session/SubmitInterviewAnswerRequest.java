package com.baseProject.myBaseProject.dto.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SubmitInterviewAnswerRequest(
        @NotNull @PositiveOrZero Integer expectedTurnIndex,
        @NotBlank @Size(max = 8000) String answer) {
}
