package com.baseProject.myBaseProject.importer;

import java.util.Set;

import com.baseProject.myBaseProject.enums.QuestionDifficulty;
import com.baseProject.myBaseProject.enums.QuestionLevel;
import com.baseProject.myBaseProject.enums.QuestionType;

public record QuestionImportValidationResult(
        boolean valid,
        String errorCode,
        String errorMessage,
        String contentVi,
        String contentEn,
        QuestionLevel level,
        QuestionType questionType,
        QuestionDifficulty difficulty,
        String companyRef,
        Set<String> techStackCodes,
        Set<String> technologyCodes,
        boolean active,
        String fingerprint
) {
    public static QuestionImportValidationResult invalid(String code, String message) {
        return new QuestionImportValidationResult(
                false, code, message, null, null, null, null, null,
                null, Set.of(), Set.of(), true, null
        );
    }
}
