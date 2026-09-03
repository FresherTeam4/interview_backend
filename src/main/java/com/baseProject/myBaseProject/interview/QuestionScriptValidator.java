package com.baseProject.myBaseProject.interview;

import static com.baseProject.myBaseProject.constant.InterviewConstraints.MAX_QUESTION_TEXT_LENGTH;

import com.baseProject.myBaseProject.enums.QuestionSourceType;
import com.baseProject.myBaseProject.exception.ScriptGenerationException;
import com.baseProject.myBaseProject.interview.ai.GeneratedQuestion;
import com.baseProject.myBaseProject.interview.ai.ScriptGenerationInput;
import com.baseProject.myBaseProject.interview.ai.ScriptGenerationOutcome;
import com.baseProject.myBaseProject.repository.projection.QuestionHistoryProjection;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
class QuestionScriptValidator {

    private static final int TOPIC_MAX_LENGTH = 150;
    private static final int COMPETENCY_MAX_LENGTH = 100;
    private static final int JD_EXCERPT_MAX_LENGTH = 2000;
    private static final int SIGNATURE_CONCEPT_MAX_LENGTH = 200;

    ValidatedScript validate(
            ScriptGenerationOutcome outcome,
            ScriptGenerationInput input,
            Set<Long> allowedProjectIds,
            Set<Long> allowedSkillIds,
            List<Long> recentSessionIds,
            List<QuestionHistoryProjection> history) {
        if (outcome == null || outcome.script() == null || outcome.script().questions() == null) {
            throw ScriptGenerationException.invalidOutput("missing questions");
        }
        validateMetadata(outcome);

        List<GeneratedQuestion> generated = outcome.script().questions();
        if (generated.size() != input.questionCount()) {
            throw ScriptGenerationException.invalidOutput(
                    "expected %d questions but received %d"
                            .formatted(input.questionCount(), generated.size()));
        }

        List<GeneratedQuestion> sorted = generated.stream()
                .sorted(Comparator.comparing(
                        GeneratedQuestion::ordinal,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<ValidatedQuestion> validated = new ArrayList<>(sorted.size());
        Set<String> normalizedTexts = new HashSet<>();
        Set<String> signatures = new HashSet<>();

        for (int index = 0; index < sorted.size(); index++) {
            GeneratedQuestion question = sorted.get(index);
            int expectedOrdinal = index + 1;
            if (question == null
                    || question.ordinal() == null
                    || question.ordinal() != expectedOrdinal) {
                throw ScriptGenerationException.invalidOutput(
                        "ordinals must be unique and continuous from 1");
            }

            String questionText = requiredPlainText(
                    question.questionText(), "questionText", MAX_QUESTION_TEXT_LENGTH);
            String topic = requiredPlainText(question.topic(), "topic", TOPIC_MAX_LENGTH);
            String competency = requiredPlainText(
                    question.competency(), "competency", COMPETENCY_MAX_LENGTH);
            String concept = requiredPlainText(
                    question.signatureConcept(),
                    "signatureConcept",
                    SIGNATURE_CONCEPT_MAX_LENGTH);
            if (question.difficulty() == null
                    || question.difficulty() < 1
                    || question.difficulty() > 5) {
                throw ScriptGenerationException.invalidOutput("difficulty must be between 1 and 5");
            }
            if (question.sourceType() == null) {
                throw ScriptGenerationException.invalidOutput("sourceType is required");
            }
            validateSourceIds(question, allowedProjectIds, allowedSkillIds);

            String jdExcerpt = matchingJdExcerpt(
                    question.sourceJdExcerpt(), input.jobDescription());
            validateSourceShape(question, jdExcerpt);

            String normalizedText = normalizeText(questionText);
            if (!normalizedTexts.add(normalizedText)) {
                throw ScriptGenerationException.invalidOutput(
                        "duplicate normalized question text");
            }

            String signature = questionSignature(question, competency, concept);
            if (!signatures.add(signature)) {
                throw ScriptGenerationException.invalidOutput("duplicate question signature");
            }

            validated.add(new ValidatedQuestion(
                    (short) expectedOrdinal,
                    questionText,
                    topic,
                    competency,
                    question.difficulty().shortValue(),
                    question.sourceType(),
                    question.sourceProjectId(),
                    question.sourceSkillId(),
                    jdExcerpt,
                    signature));
        }

        validateDiversity(validated, recentSessionIds, history);
        return new ValidatedScript(
                validated,
                outcome.modelName().strip(),
                outcome.promptVersion().strip(),
                outcome.tokenCost(),
                outcome.durationMs());
    }

    void validateDiversity(
            List<ValidatedQuestion> questions,
            List<Long> recentSessionIds,
            List<QuestionHistoryProjection> history) {
        if (history.isEmpty()) {
            return;
        }

        Set<String> historicalTexts = new HashSet<>();
        for (QuestionHistoryProjection previous : history) {
            historicalTexts.add(normalizeText(previous.questionText()));
        }
        boolean repeatedText = questions.stream()
                .map(ValidatedQuestion::questionText)
                .map(QuestionScriptValidator::normalizeText)
                .anyMatch(historicalTexts::contains);
        if (repeatedText) {
            throw ScriptGenerationException.diversityRejected(
                    "a normalized question repeats one of the three recent sessions");
        }

        if (recentSessionIds.isEmpty()) {
            return;
        }
        Long nearestSessionId = recentSessionIds.get(0);
        Set<String> nearestSignatures = new HashSet<>();
        for (QuestionHistoryProjection previous : history) {
            if (nearestSessionId.equals(previous.sessionId())) {
                nearestSignatures.add(previous.questionSignature());
            }
        }
        long differentCount = questions.stream()
                .map(ValidatedQuestion::questionSignature)
                .filter(signature -> !nearestSignatures.contains(signature))
                .count();
        if (differentCount * 10 < questions.size() * 7L) {
            throw ScriptGenerationException.diversityRejected(
                    "less than 70 percent of signatures differ from the nearest session");
        }
    }

    private static void validateMetadata(ScriptGenerationOutcome outcome) {
        requiredPlainText(outcome.modelName(), "modelName", 100);
        requiredPlainText(outcome.promptVersion(), "promptVersion", 20);
        if (outcome.durationMs() < 0 || outcome.tokenCost() != null && outcome.tokenCost() < 0) {
            throw ScriptGenerationException.invalidOutput("invalid provider metadata");
        }
    }

    private static void validateSourceIds(
            GeneratedQuestion question,
            Set<Long> allowedProjectIds,
            Set<Long> allowedSkillIds) {
        if (question.sourceProjectId() != null
                && !allowedProjectIds.contains(question.sourceProjectId())) {
            throw ScriptGenerationException.invalidOutput(
                    "sourceProjectId is not in the selected profile snapshot");
        }
        if (question.sourceSkillId() != null
                && !allowedSkillIds.contains(question.sourceSkillId())) {
            throw ScriptGenerationException.invalidOutput(
                    "sourceSkillId is not in the selected profile snapshot");
        }
    }

    private static void validateSourceShape(GeneratedQuestion question, String jdExcerpt) {
        Long projectId = question.sourceProjectId();
        Long skillId = question.sourceSkillId();
        switch (question.sourceType()) {
            case CV_PROJECT -> require(projectId != null, "CV_PROJECT requires sourceProjectId");
            case CV_SKILL -> require(skillId != null, "CV_SKILL requires sourceSkillId");
            case CV_JD_MATCH -> require(
                    (projectId != null || skillId != null) && jdExcerpt != null,
                    "CV_JD_MATCH requires a CV source and matching JD excerpt");
            case JD_GAP -> require(
                    projectId == null && skillId == null && jdExcerpt != null,
                    "JD_GAP only accepts a matching JD excerpt");
            case GENERAL_BEHAVIORAL -> require(
                    projectId == null && skillId == null && jdExcerpt == null,
                    "GENERAL_BEHAVIORAL must not carry source references");
        }
    }

    private static String matchingJdExcerpt(String excerpt, String jobDescription) {
        if (excerpt == null || excerpt.isBlank()) {
            return null;
        }
        String normalized = requiredPlainText(excerpt, "sourceJdExcerpt", JD_EXCERPT_MAX_LENGTH);
        return normalizeText(jobDescription).contains(normalizeText(normalized)) ? normalized : null;
    }

    private static String questionSignature(
            GeneratedQuestion question,
            String competency,
            String concept) {
        String material = String.join("|",
                question.sourceType().name(),
                question.sourceProjectId() == null ? "-" : question.sourceProjectId().toString(),
                question.sourceSkillId() == null ? "-" : question.sourceSkillId().toString(),
                normalizeText(competency),
                normalizeText(concept));
        return sha256Hex(material);
    }

    private static String requiredPlainText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw ScriptGenerationException.invalidOutput(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw ScriptGenerationException.invalidOutput(field + " exceeds its length limit");
        }
        if (normalized.indexOf('\0') >= 0
                || normalized.contains("```")
                || normalized.contains("~~~")) {
            throw ScriptGenerationException.invalidOutput(field + " contains markup or code fence");
        }
        return normalized;
    }

    private static String normalizeText(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip()
                .replaceAll("\\s+", " ");
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required but not available", exception);
        }
    }

    private static void require(boolean condition, String detail) {
        if (!condition) {
            throw ScriptGenerationException.invalidOutput(detail);
        }
    }
}
