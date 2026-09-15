package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.config.properites.InterviewScoringProperties;
import com.baseProject.myBaseProject.dto.ai.interview.InterviewAssessmentResult;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.interview.model.InterviewScoreCalculation;
import com.baseProject.myBaseProject.interview.model.InterviewScoringContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class InterviewScoreCalculator {
    private static final int SCORE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final InterviewScoringProperties properties;

    public InterviewScoreCalculation calculate(
            InterviewScoringContext context, InterviewAssessmentResult assessment) {
        Map<String, InterviewAssessmentResult.FocusAreaAssessment> assessmentsByCode =
                indexAssessmentsByCode(assessment.focusAreaAssessments());

        // Backend tự áp dụng trọng số nghiệp vụ thay vì để AI quyết định điểm tổng hợp.
        BigDecimal coveragePercentage = calculateCoveragePercentage(
                context.focusAreas(), assessmentsByCode);
        BigDecimal technicalScore = calculateTechnicalScore(
                context.focusAreas(), assessmentsByCode);
        BigDecimal communicationScore = normalizeScore(assessment.communicationScore());
        BigDecimal overallScore = calculateOverallScore(
                coveragePercentage, technicalScore, communicationScore);

        return new InterviewScoreCalculation(
                technicalScore,
                communicationScore,
                overallScore,
                coveragePercentage);
    }

    private Map<String, InterviewAssessmentResult.FocusAreaAssessment> indexAssessmentsByCode(
            List<InterviewAssessmentResult.FocusAreaAssessment> assessments) {
        Map<String, InterviewAssessmentResult.FocusAreaAssessment> assessmentsByCode =
                new HashMap<>();
        for (InterviewAssessmentResult.FocusAreaAssessment assessment : assessments) {
            assessmentsByCode.put(assessment.focusAreaCode(), assessment);
        }
        return assessmentsByCode;
    }

    private BigDecimal calculateCoveragePercentage(
            List<InterviewScoringContext.FocusArea> focusAreas,
            Map<String, InterviewAssessmentResult.FocusAreaAssessment> assessmentsByCode) {
        BigDecimal totalPriorityWeight = BigDecimal.ZERO;
        BigDecimal coveredPriorityWeight = BigDecimal.ZERO;

        for (InterviewScoringContext.FocusArea focusArea : focusAreas) {
            BigDecimal weight = priorityWeight(focusArea.priority());
            InterviewAssessmentResult.FocusAreaAssessment assessment =
                    requireAssessment(focusArea, assessmentsByCode);

            totalPriorityWeight = totalPriorityWeight.add(weight);
            coveredPriorityWeight = coveredPriorityWeight.add(
                    weight.multiply(coverageFactor(assessment.evidenceStatus())));
        }

        return percentage(coveredPriorityWeight, totalPriorityWeight);
    }

    private BigDecimal calculateTechnicalScore(
            List<InterviewScoringContext.FocusArea> focusAreas,
            Map<String, InterviewAssessmentResult.FocusAreaAssessment> assessmentsByCode) {
        BigDecimal assessedPriorityWeight = BigDecimal.ZERO;
        BigDecimal weightedTechnicalScoreSum = BigDecimal.ZERO;

        for (InterviewScoringContext.FocusArea focusArea : focusAreas) {
            InterviewAssessmentResult.FocusAreaAssessment assessment =
                    requireAssessment(focusArea, assessmentsByCode);
            if (assessment.score() == null) {
                continue;
            }

            BigDecimal weight = priorityWeight(focusArea.priority());
            assessedPriorityWeight = assessedPriorityWeight.add(weight);
            weightedTechnicalScoreSum = weightedTechnicalScoreSum.add(
                    weight.multiply(BigDecimal.valueOf(assessment.score())));
        }

        if (assessedPriorityWeight.signum() == 0) {
            return null;
        }
        return weightedTechnicalScoreSum.divide(
                assessedPriorityWeight, SCORE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeScore(Integer score) {
        return score == null ? null : scaled(BigDecimal.valueOf(score));
    }

    private BigDecimal calculateOverallScore(
            BigDecimal coveragePercentage,
            BigDecimal technicalScore,
            BigDecimal communicationScore) {
        boolean hasEnoughCoverage = coveragePercentage.compareTo(
                properties.minimumCoveragePercentage()) >= 0;

        // Không công bố overall score khi buổi phỏng vấn chưa thu đủ evidence tối thiểu.
        if (!hasEnoughCoverage || technicalScore == null || communicationScore == null) {
            return null;
        }

        return scaled(
                technicalScore.multiply(properties.technicalWeight())
                        .add(communicationScore.multiply(properties.communicationWeight())));
    }

    private InterviewAssessmentResult.FocusAreaAssessment requireAssessment(
            InterviewScoringContext.FocusArea focusArea,
            Map<String, InterviewAssessmentResult.FocusAreaAssessment> assessmentsByCode) {
        return Objects.requireNonNull(
                assessmentsByCode.get(focusArea.code()),
                () -> "Missing assessment for focus area: " + focusArea.code());
    }

    private BigDecimal priorityWeight(InterviewFocusPriority priority) {
        return BigDecimal.valueOf(switch (priority) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        });
    }

    private BigDecimal coverageFactor(InterviewEvidenceStatus status) {
        return switch (status) {
            case NOT_EXPLORED -> BigDecimal.ZERO;
            case PARTIAL -> new BigDecimal("0.5");
            case SUFFICIENT -> BigDecimal.ONE;
        };
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(SCORE_SCALE);
        }
        return scaled(value.multiply(ONE_HUNDRED)
                .divide(total, SCORE_SCALE + 2, RoundingMode.HALF_UP));
    }

    private BigDecimal scaled(BigDecimal value) {
        return value.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }
}
