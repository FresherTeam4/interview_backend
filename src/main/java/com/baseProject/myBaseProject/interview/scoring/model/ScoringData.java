package com.baseProject.myBaseProject.interview.scoring.model;

import com.baseProject.myBaseProject.enums.ReportHighlightType;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringInput;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ScoringData {

    private ScoringData() {
    }

    public record ScoringPreparation(
            ScoringInput input,
            Map<String, CriterionReference> criteriaByCode,
            Map<Long, TurnReference> turnsById,
            boolean partial) {

        public ScoringPreparation {
            criteriaByCode = Map.copyOf(criteriaByCode);
            turnsById = Map.copyOf(turnsById);
        }
    }

    public record CriterionReference(
            Long id,
            String code,
            String name,
            BigDecimal weight,
            BigDecimal maxScore,
            Map<Short, BigDecimal> scoresByLevel) {

        public CriterionReference {
            scoresByLevel = Map.copyOf(scoresByLevel);
        }
    }

    public record TurnReference(Long id, String answer) {
    }

    public record ValidatedScoringResult(
            List<ValidatedCriterionScore> criteria,
            BigDecimal overallScore,
            BigDecimal completionRatio,
            BigDecimal assessedWeight,
            boolean partial,
            String summary,
            List<ValidatedHighlight> highlights,
            String modelName,
            String promptVersion,
            int durationMs) {

        public ValidatedScoringResult {
            criteria = List.copyOf(criteria);
            highlights = List.copyOf(highlights);
        }
    }

    public record ValidatedCriterionScore(
            Long criterionId,
            BigDecimal score,
            BigDecimal maxScore,
            short levelNo,
            String comment,
            List<ValidatedEvidence> evidences) {

        public ValidatedCriterionScore {
            evidences = List.copyOf(evidences);
        }
    }

    public record ValidatedEvidence(
            Long turnId,
            String quote,
            int startOffset,
            int endOffset) {
    }

    public record ValidatedHighlight(
            ReportHighlightType type,
            String content,
            short displayOrder) {
    }
}
