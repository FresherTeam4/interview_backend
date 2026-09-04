package com.baseProject.myBaseProject.interview.ai.model;

import com.baseProject.myBaseProject.enums.SessionEndReason;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Immutable provider contract for scoring one interview transcript. */
public final class ScoringContract {

    private ScoringContract() {
    }

    public record ScoringInput(
            String languageCode,
            BigDecimal completionRatio,
            SessionEndReason endReason,
            List<ScoringCriterionInput> criteria,
            List<ScoringTurnInput> candidateTurns) {

        public ScoringInput {
            Objects.requireNonNull(languageCode);
            Objects.requireNonNull(completionRatio);
            Objects.requireNonNull(endReason);
            criteria = List.copyOf(criteria);
            candidateTurns = List.copyOf(candidateTurns);
        }
    }

    public record ScoringCriterionInput(
            String code,
            String name,
            String description,
            BigDecimal weight,
            short maxScore,
            List<ScoringLevelInput> levels) {

        public ScoringCriterionInput {
            levels = List.copyOf(levels);
        }
    }

    public record ScoringLevelInput(
            short levelNo,
            String label,
            String descriptor,
            BigDecimal scoreValue) {
    }

    public record ScoringTurnInput(
            Long turnId,
            int turnIndex,
            String question,
            String answer) {
    }

    public record ScoringOutcome(
            GeneratedScoringResult result,
            String modelName,
            String promptVersion,
            Integer tokenCost,
            int durationMs) {
    }

    public record GeneratedScoringResult(
            List<GeneratedCriterionScore> criteria,
            String summary,
            List<String> strengths,
            List<String> improvements,
            List<String> nextActions) {
    }

    public record GeneratedCriterionScore(
            String criterionCode,
            short levelNo,
            BigDecimal score,
            String comment,
            List<GeneratedEvidence> evidences) {
    }

    public record GeneratedEvidence(Long turnId, String quoteText) {
    }
}
