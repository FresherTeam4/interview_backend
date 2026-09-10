package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.config.properites.InterviewScoringProperties;
import com.baseProject.myBaseProject.dto.ai.interview.InterviewAssessmentResult;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.interview.model.InterviewScoreCalculation;
import com.baseProject.myBaseProject.interview.model.InterviewScoringContext;
import com.baseProject.myBaseProject.interview.support.InterviewScoreCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewScoreCalculatorTest {
    private final InterviewScoreCalculator calculator = new InterviewScoreCalculator(
            new InterviewScoringProperties(
                    new BigDecimal("60"),
                    new BigDecimal("0.8"),
                    new BigDecimal("0.2")));

    @Test
    void calculatesWeightedScoresAndCoverage() {
        InterviewScoreCalculation calculation = calculator.calculate(
                context(), assessment(
                        scored("BACKEND", 80, InterviewEvidenceStatus.SUFFICIENT),
                        scored("DATABASE", 50, InterviewEvidenceStatus.PARTIAL),
                        unexplored("CLOUD"),
                        70));

        assertThat(calculation.technicalScore()).isEqualByComparingTo("68.00");
        assertThat(calculation.communicationScore()).isEqualByComparingTo("70.00");
        assertThat(calculation.coveragePercentage()).isEqualByComparingTo("66.67");
        assertThat(calculation.overallScore()).isEqualByComparingTo("68.40");
    }

    @Test
    void withholdsOverallScoreWhenCoverageIsBelowThreshold() {
        InterviewScoreCalculation calculation = calculator.calculate(
                context(), assessment(
                        scored("BACKEND", 80, InterviewEvidenceStatus.PARTIAL),
                        unexplored("DATABASE"),
                        unexplored("CLOUD"),
                        75));

        assertThat(calculation.technicalScore()).isEqualByComparingTo("80.00");
        assertThat(calculation.coveragePercentage()).isEqualByComparingTo("25.00");
        assertThat(calculation.overallScore()).isNull();
    }

    @Test
    void withholdsOverallScoreWhenCommunicationCannotBeScored() {
        InterviewScoreCalculation calculation = calculator.calculate(
                context(), assessment(
                        scored("BACKEND", 80, InterviewEvidenceStatus.SUFFICIENT),
                        scored("DATABASE", 70, InterviewEvidenceStatus.SUFFICIENT),
                        scored("CLOUD", 60, InterviewEvidenceStatus.SUFFICIENT),
                        null));

        assertThat(calculation.coveragePercentage()).isEqualByComparingTo("100.00");
        assertThat(calculation.communicationScore()).isNull();
        assertThat(calculation.overallScore()).isNull();
    }

    private InterviewAssessmentResult assessment(
            InterviewAssessmentResult.FocusAreaAssessment backend,
            InterviewAssessmentResult.FocusAreaAssessment database,
            InterviewAssessmentResult.FocusAreaAssessment cloud,
            Integer communicationScore) {
        return new InterviewAssessmentResult(
                "Summary",
                "Technical feedback",
                List.of(backend, database, cloud),
                communicationScore,
                "Communication feedback",
                List.of());
    }

    private InterviewAssessmentResult.FocusAreaAssessment scored(
            String code, int score, InterviewEvidenceStatus status) {
        return new InterviewAssessmentResult.FocusAreaAssessment(
                code,
                score,
                status,
                List.of(11L));
    }

    private InterviewAssessmentResult.FocusAreaAssessment unexplored(String code) {
        return new InterviewAssessmentResult.FocusAreaAssessment(
                code,
                null,
                InterviewEvidenceStatus.NOT_EXPLORED,
                List.of());
    }

    private InterviewScoringContext context() {
        return new InterviewScoringContext(
                501L,
                null,
                "vi",
                null,
                1200,
                null,
                "Backend role",
                null,
                List.of(
                        area(21L, "BACKEND", InterviewFocusPriority.HIGH),
                        area(22L, "DATABASE", InterviewFocusPriority.MEDIUM),
                        area(23L, "CLOUD", InterviewFocusPriority.LOW)),
                List.of());
    }

    private InterviewScoringContext.FocusArea area(
            Long id, String code, InterviewFocusPriority priority) {
        return new InterviewScoringContext.FocusArea(
                id,
                code,
                code,
                code,
                priority,
                "Required",
                InterviewEvidenceStatus.NOT_EXPLORED,
                null);
    }
}
