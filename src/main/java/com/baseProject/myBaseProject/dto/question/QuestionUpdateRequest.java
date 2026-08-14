package com.baseProject.myBaseProject.dto.question;

import com.baseProject.myBaseProject.enums.QuestionDifficulty;
import com.baseProject.myBaseProject.enums.QuestionLevel;
import com.baseProject.myBaseProject.enums.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record QuestionUpdateRequest(
        @NotBlank(message = "Vietnamese content is required")
        String contentVi,

        String contentEn,

        @Positive(message = "Tech stack id must be positive")
        Integer techStackId,

        @NotNull(message = "Question level is required")
        QuestionLevel level,

        @NotNull(message = "Question type is required")
        QuestionType questionType,

        @NotNull(message = "Difficulty is required")
        QuestionDifficulty difficulty,

        @Size(max = 150, message = "Company reference must not exceed 150 characters")
        String companyRef,

        @NotNull(message = "Active status is required")
        Boolean active,

        @NotNull(message = "Version is required")
        @PositiveOrZero(message = "Version must not be negative")
        Integer version
) {
}
