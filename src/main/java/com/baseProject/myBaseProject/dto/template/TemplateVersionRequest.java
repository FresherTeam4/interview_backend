package com.baseProject.myBaseProject.dto.template;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TemplateVersionRequest(
        @NotNull @PositiveOrZero Long expectedVersion) {
}
