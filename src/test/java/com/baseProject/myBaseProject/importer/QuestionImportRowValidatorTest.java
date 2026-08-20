package com.baseProject.myBaseProject.importer;

import java.util.Map;

import com.baseProject.myBaseProject.entity.QuestionImportRow;
import com.baseProject.myBaseProject.enums.QuestionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionImportRowValidatorTest {
    private final QuestionImportRowValidator validator = new QuestionImportRowValidator();

    @Test
    void validatesAndNormalizesATechnicalQuestion() {
        QuestionImportRow row = baseRow();
        row.setTechStackCodes(" backend | devops ");
        row.setTechnologyCodes("java|docker");

        QuestionImportValidationResult result = validator.validate(
                row,
                Map.of("BACKEND", new Object(), "DEVOPS", new Object()),
                Map.of("JAVA", new Object(), "DOCKER", new Object())
        );

        assertTrue(result.valid());
        assertEquals(QuestionType.TECHNICAL, result.questionType());
        assertEquals("REST API là gì?", result.contentVi());
        assertEquals(2, result.techStackCodes().size());
        assertTrue(result.active());
    }

    @Test
    void rejectsTechnicalQuestionWithoutTechStack() {
        QuestionImportValidationResult result = validator.validate(
                baseRow(),
                Map.of("BACKEND", new Object()),
                Map.of()
        );

        assertFalse(result.valid());
        assertEquals("TECH_STACK_REQUIRED", result.errorCode());
    }

    @Test
    void rejectsUnknownTechnologyCode() {
        QuestionImportRow row = baseRow();
        row.setTechStackCodes("BACKEND");
        row.setTechnologyCodes("UNKNOWN");

        QuestionImportValidationResult result = validator.validate(
                row,
                Map.of("BACKEND", new Object()),
                Map.of("JAVA", new Object())
        );

        assertFalse(result.valid());
        assertEquals("UNKNOWN_TECHNOLOGY", result.errorCode());
    }

    @Test
    void rejectsContentThatCannotFitTheQuestionContract() {
        QuestionImportRow row = baseRow();
        row.setContentVi("x".repeat(10_001));

        QuestionImportValidationResult result = validator.validate(row, Map.of(), Map.of());

        assertFalse(result.valid());
        assertEquals("CONTENT_VI_TOO_LONG", result.errorCode());
    }

    private static QuestionImportRow baseRow() {
        return QuestionImportRow.builder()
                .contentVi(" REST API là gì? ")
                .levelValue("junior")
                .questionTypeValue("technical")
                .difficultyValue("easy")
                .activeValue("")
                .techStackCodes("")
                .technologyCodes("")
                .build();
    }
}
