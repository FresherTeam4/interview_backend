package com.baseProject.myBaseProject.importer;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baseProject.myBaseProject.entity.QuestionImportRow;
import com.baseProject.myBaseProject.enums.QuestionDifficulty;
import com.baseProject.myBaseProject.enums.QuestionLevel;
import com.baseProject.myBaseProject.enums.QuestionType;
import com.baseProject.myBaseProject.util.QuestionFingerprint;
import org.springframework.stereotype.Component;

@Component
public class QuestionImportRowValidator {
    private static final int MAX_TAXONOMY_VALUES = 20;
    private static final int MAX_CONTENT_CHARACTERS = 10_000;

    public QuestionImportValidationResult validate(
            QuestionImportRow row,
            Map<String, ?> activeTechStacks,
            Map<String, ?> activeTechnologies
    ) {
        String contentVi = normalizeRequired(row.getContentVi());
        if (contentVi == null) {
            return invalid("CONTENT_VI_REQUIRED", "content_vi is required");
        }
        if (contentVi.length() > MAX_CONTENT_CHARACTERS) {
            return invalid(
                    "CONTENT_VI_TOO_LONG",
                    "content_vi must not exceed 10000 characters"
            );
        }
        String contentEn = normalizeOptional(row.getContentEn());
        if (contentEn != null && contentEn.length() > MAX_CONTENT_CHARACTERS) {
            return invalid(
                    "CONTENT_EN_TOO_LONG",
                    "content_en must not exceed 10000 characters"
            );
        }
        String companyRef = normalizeOptional(row.getCompanyRef());
        if (companyRef != null && companyRef.length() > 150) {
            return invalid("COMPANY_REF_TOO_LONG", "company_ref must not exceed 150 characters");
        }

        QuestionLevel level = parseEnum(row.getLevelValue(), QuestionLevel.class);
        if (level == null) {
            return invalid("INVALID_LEVEL", "level must be FRESHER, JUNIOR, MID or SENIOR");
        }
        QuestionType questionType = parseEnum(row.getQuestionTypeValue(), QuestionType.class);
        if (questionType == null) {
            return invalid(
                    "INVALID_QUESTION_TYPE",
                    "question_type must be BEHAVIORAL, TECHNICAL or CASE_STUDY"
            );
        }
        QuestionDifficulty difficulty = parseEnum(
                row.getDifficultyValue(),
                QuestionDifficulty.class
        );
        if (difficulty == null) {
            return invalid("INVALID_DIFFICULTY", "difficulty must be EASY, MEDIUM or HARD");
        }

        Boolean active = parseBoolean(row.getActiveValue());
        if (active == null) {
            return invalid("INVALID_ACTIVE", "active must be true, false or blank");
        }

        Set<String> techStackCodes = parseCodes(row.getTechStackCodes());
        Set<String> technologyCodes = parseCodes(row.getTechnologyCodes());
        if (techStackCodes.size() > MAX_TAXONOMY_VALUES) {
            return invalid("TOO_MANY_TECH_STACKS", "A question must not have more than 20 tech stacks");
        }
        if (technologyCodes.size() > MAX_TAXONOMY_VALUES) {
            return invalid("TOO_MANY_TECHNOLOGIES", "A question must not have more than 20 technologies");
        }
        Set<String> missingStacks = missingCodes(techStackCodes, activeTechStacks.keySet());
        if (!missingStacks.isEmpty()) {
            return invalid(
                    "UNKNOWN_TECH_STACK",
                    "Unknown or inactive tech stack codes: " + String.join("|", missingStacks)
            );
        }
        Set<String> missingTechnologies = missingCodes(
                technologyCodes,
                activeTechnologies.keySet()
        );
        if (!missingTechnologies.isEmpty()) {
            return invalid(
                    "UNKNOWN_TECHNOLOGY",
                    "Unknown or inactive technology codes: " + String.join("|", missingTechnologies)
            );
        }
        if (questionType == QuestionType.TECHNICAL && techStackCodes.isEmpty()) {
            return invalid(
                    "TECH_STACK_REQUIRED",
                    "Technical questions require at least one tech stack"
            );
        }

        return new QuestionImportValidationResult(
                true,
                null,
                null,
                contentVi,
                contentEn,
                level,
                questionType,
                difficulty,
                companyRef,
                techStackCodes,
                technologyCodes,
                active,
                QuestionFingerprint.sha256(contentVi)
        );
    }

    public static Set<String> parseCodes(String raw) {
        String normalized = normalizeOptional(raw);
        if (normalized == null) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(normalized.split("\\|", -1))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> missingCodes(Set<String> requested, Collection<String> available) {
        Set<String> missing = new LinkedHashSet<>(requested);
        missing.removeAll(available);
        return missing;
    }

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> enumType) {
        String normalized = normalizeOptional(raw);
        if (normalized == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Boolean parseBoolean(String raw) {
        String normalized = normalizeOptional(raw);
        if (normalized == null) {
            return Boolean.TRUE;
        }
        if ("true".equalsIgnoreCase(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static QuestionImportValidationResult invalid(String code, String message) {
        return QuestionImportValidationResult.invalid(code, message);
    }
}
