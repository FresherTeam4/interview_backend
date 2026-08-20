package com.baseProject.myBaseProject.dto.question.imports;

import com.baseProject.myBaseProject.entity.QuestionImportRow;
import com.baseProject.myBaseProject.enums.QuestionImportRowStatus;

public record QuestionImportRowResponse(
        Long id,
        int rowNumber,
        String contentVi,
        String contentEn,
        String level,
        String questionType,
        String difficulty,
        String companyRef,
        String techStackCodes,
        String technologyCodes,
        String active,
        QuestionImportRowStatus status,
        String errorCode,
        String errorMessage,
        Long existingQuestionId,
        Long createdQuestionId
) {
    public static QuestionImportRowResponse from(QuestionImportRow row) {
        return new QuestionImportRowResponse(
                row.getId(),
                row.getRowNumber(),
                row.getContentVi(),
                row.getContentEn(),
                row.getLevelValue(),
                row.getQuestionTypeValue(),
                row.getDifficultyValue(),
                row.getCompanyRef(),
                row.getTechStackCodes(),
                row.getTechnologyCodes(),
                row.getActiveValue(),
                row.getStatus(),
                row.getErrorCode(),
                row.getErrorMessage(),
                row.getExistingQuestion() == null ? null : row.getExistingQuestion().getId(),
                row.getCreatedQuestion() == null ? null : row.getCreatedQuestion().getId()
        );
    }
}
