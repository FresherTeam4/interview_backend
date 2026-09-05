package com.baseProject.myBaseProject.dto.template;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ConfirmInterviewTemplateRequest(
        @NotNull @PositiveOrZero Long expectedVersion) {
}
