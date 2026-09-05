package com.baseProject.myBaseProject.dto.template;

import com.baseProject.myBaseProject.dto.ai.JobAnalysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateInterviewTemplateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull JobAnalysis content,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
