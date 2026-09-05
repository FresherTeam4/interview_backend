package com.baseProject.myBaseProject.dto.template;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PublishInterviewTemplateRequest(
        @NotNull @PositiveOrZero Long expectedVersion) {
}
