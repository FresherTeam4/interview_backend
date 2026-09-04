package com.baseProject.myBaseProject.interview.scoring;

import com.baseProject.myBaseProject.enums.ReportHighlightType;
import com.baseProject.myBaseProject.exception.InterviewScoringException;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.GeneratedCriterionScore;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.GeneratedEvidence;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.GeneratedScoringResult;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringOutcome;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.CriterionReference;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.ScoringPreparation;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.TurnReference;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.ValidatedCriterionScore;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.ValidatedEvidence;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.ValidatedHighlight;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.ValidatedScoringResult;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class InterviewScoringValidator {

    private static final int SUMMARY_MAX_LENGTH = 4000;
    private static final int COMMENT_MAX_LENGTH = 2000;
    private static final int EVIDENCE_MAX_LENGTH = 2000;
    private static final int MAX_EVIDENCES_PER_CRITERION = 5;
    private static final int HIGHLIGHT_MAX_LENGTH = 500;
    private static final int MAX_HIGHLIGHTS_PER_TYPE = 5;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public ValidatedScoringResult validate(
            ScoringOutcome outcome,
            ScoringPreparation preparation,
            String expectedPromptVersion) {
        validateMetadata(outcome, expectedPromptVersion);
        GeneratedScoringResult generated = outcome.result();
        if (generated == null || generated.criteria() == null) {
            throw invalid("criteria are missing");
        }

        List<ValidatedCriterionScore> criteria = validateCriteria(
                generated.criteria(),
                preparation);
        if (criteria.isEmpty()) {
            throw invalid("no criterion has valid evidence");
        }
        if (!preparation.partial()
                && criteria.size() != preparation.criteriaByCode().size()) {
            throw invalid("a completed interview must assess every criterion");
        }

        BigDecimal assessedWeight = criteria.stream()
                .map(score -> criterionById(preparation, score.criterionId()).weight())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_UP);
        if (assessedWeight.signum() <= 0 || assessedWeight.compareTo(ONE) > 0) {
            throw invalid("assessed weight is outside the rubric range");
        }

        BigDecimal weightedScore = criteria.stream()
                .map(score -> {
                    CriterionReference criterion = criterionById(
                            preparation,
                            score.criterionId());
                    return score.score()
                            .divide(score.maxScore(), 8, RoundingMode.HALF_UP)
                            .multiply(criterion.weight());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overallScore = ONE_HUNDRED
                .multiply(weightedScore)
                .divide(assessedWeight, 2, RoundingMode.HALF_UP);

        String summary = requiredPlainText(
                generated.summary(),
                SUMMARY_MAX_LENGTH,
                "summary");
        List<ValidatedHighlight> highlights = new ArrayList<>();
        addHighlights(
                highlights,
                ReportHighlightType.STRENGTH,
                generated.strengths());
        addHighlights(
                highlights,
                ReportHighlightType.IMPROVEMENT,
                generated.improvements());
        addHighlights(
                highlights,
                ReportHighlightType.NEXT_ACTION,
                generated.nextActions());

        return new ValidatedScoringResult(
                criteria,
                overallScore,
                preparation.input().completionRatio(),
                assessedWeight,
                preparation.partial(),
                summary,
                highlights,
                outcome.modelName().strip(),
                outcome.promptVersion().strip(),
                outcome.durationMs());
    }

    private List<ValidatedCriterionScore> validateCriteria(
            List<GeneratedCriterionScore> generatedCriteria,
            ScoringPreparation preparation) {
        List<ValidatedCriterionScore> validated = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        for (GeneratedCriterionScore generated : generatedCriteria) {
            if (generated == null || generated.criterionCode() == null) {
                throw invalid("criterion code is missing");
            }
            String code = generated.criterionCode().strip();
            CriterionReference criterion = preparation.criteriaByCode().get(code);
            if (criterion == null || !seenCodes.add(code)) {
                throw invalid("criterion is unknown or duplicated: " + code);
            }
            BigDecimal allowedScore = criterion.scoresByLevel().get(generated.levelNo());
            if (allowedScore == null
                    || generated.score() == null
                    || generated.score().compareTo(allowedScore) != 0) {
                throw invalid("score does not match rubric level for " + code);
            }
            String comment = requiredPlainText(
                    generated.comment(),
                    COMMENT_MAX_LENGTH,
                    "criterion comment");
            List<ValidatedEvidence> evidences = validateEvidences(
                    generated.evidences(),
                    preparation.turnsById());
            if (evidences.isEmpty()) {
                throw invalid("criterion has no valid evidence: " + code);
            }
            validated.add(new ValidatedCriterionScore(
                    criterion.id(),
                    allowedScore,
                    criterion.maxScore(),
                    generated.levelNo(),
                    comment,
                    evidences));
        }
        return validated;
    }

    private List<ValidatedEvidence> validateEvidences(
            List<GeneratedEvidence> generatedEvidences,
            java.util.Map<Long, TurnReference> turnsById) {
        if (generatedEvidences == null) {
            return List.of();
        }
        if (generatedEvidences.size() > MAX_EVIDENCES_PER_CRITERION) {
            throw invalid("too many evidences for one criterion");
        }
        List<ValidatedEvidence> result = new ArrayList<>();
        for (GeneratedEvidence generated : generatedEvidences) {
            if (generated == null || generated.turnId() == null) {
                throw invalid("evidence turn is missing");
            }
            TurnReference turn = turnsById.get(generated.turnId());
            String quote = requiredPlainText(
                    generated.quoteText(),
                    EVIDENCE_MAX_LENGTH,
                    "evidence quote");
            int startOffset = turn == null ? -1 : turn.answer().indexOf(quote);
            if (startOffset < 0) {
                throw invalid("evidence quote does not belong to candidate turn");
            }
            result.add(new ValidatedEvidence(
                    turn.id(),
                    quote,
                    startOffset,
                    startOffset + quote.length()));
        }
        return result;
    }

    private void addHighlights(
            List<ValidatedHighlight> target,
            ReportHighlightType type,
            List<String> values) {
        if (values == null) {
            throw invalid(type.name().toLowerCase() + " highlights are missing");
        }
        if (values.size() > MAX_HIGHLIGHTS_PER_TYPE) {
            throw invalid("too many " + type.name().toLowerCase() + " highlights");
        }
        for (short index = 0; index < values.size(); index++) {
            target.add(new ValidatedHighlight(
                    type,
                    requiredPlainText(
                            values.get(index),
                            HIGHLIGHT_MAX_LENGTH,
                            "highlight"),
                    index));
        }
    }

    private CriterionReference criterionById(
            ScoringPreparation preparation,
            Long criterionId) {
        return preparation.criteriaByCode().values().stream()
                .filter(criterion -> criterion.id().equals(criterionId))
                .findFirst()
                .orElseThrow(() -> invalid("criterion reference is inconsistent"));
    }

    private void validateMetadata(ScoringOutcome outcome, String expectedPromptVersion) {
        if (outcome == null
                || outcome.modelName() == null
                || outcome.modelName().isBlank()
                || outcome.modelName().strip().length() > 100
                || outcome.promptVersion() == null
                || !expectedPromptVersion.equals(outcome.promptVersion().strip())
                || outcome.durationMs() < 0
                || outcome.tokenCost() != null && outcome.tokenCost() < 0) {
            throw invalid("provider returned invalid metadata or prompt version");
        }
    }

    private String requiredPlainText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw invalid(fieldName + " is blank");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength
                || normalized.indexOf('\0') >= 0
                || normalized.contains("```")
                || normalized.contains("~~~")
                || normalized.contains("<script")) {
            throw invalid(fieldName + " is not safe plain text");
        }
        return normalized;
    }

    private InterviewScoringException invalid(String detail) {
        return InterviewScoringException.invalidOutput(detail);
    }
}
