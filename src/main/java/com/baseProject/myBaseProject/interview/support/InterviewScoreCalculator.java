package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.config.properites.InterviewScoringProperties;
import com.baseProject.myBaseProject.dto.ai.interview.InterviewAssessmentResult;
import com.baseProject.myBaseProject.enums.InterviewAssessmentConfidence;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.interview.model.InterviewScoreCalculation;
import com.baseProject.myBaseProject.interview.model.InterviewScoringContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class InterviewScoreCalculator {
    private static final int SCORE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final InterviewScoringProperties properties;

    public InterviewScoreCalculation calculate(
            InterviewScoringContext context, InterviewAssessmentResult assessment) {
        // Backend tự áp dụng trọng số nghiệp vụ thay vì để AI quyết định điểm tổng hợp.
        Map<String, InterviewAssessmentResult.FocusAreaAssessment> resultsByCode =
                new HashMap<>();
        for (InterviewAssessmentResult.FocusAreaAssessment result
                : assessment.focusAreaAssessments()) {
            resultsByCode.put(result.focusAreaCode(), result);
        }

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal coveredWeight = BigDecimal.ZERO;
        BigDecimal scoredWeight = BigDecimal.ZERO;
        BigDecimal weightedScore = BigDecimal.ZERO;
        for (InterviewScoringContext.FocusArea area : context.focusAreas()) {
            BigDecimal weight = priorityWeight(area.priority());
            InterviewAssessmentResult.FocusAreaAssessment result =
                    resultsByCode.get(area.code());
            totalWeight = totalWeight.add(weight);
            coveredWeight = coveredWeight.add(
                    weight.multiply(coverageFactor(result.evidenceStatus())));
            if (result.score() != null) {
                scoredWeight = scoredWeight.add(weight);
                weightedScore = weightedScore.add(
                        weight.multiply(BigDecimal.valueOf(result.score())));
            }
        }

        BigDecimal coverage = percentage(coveredWeight, totalWeight);
        BigDecimal technical = scoredWeight.signum() == 0
                ? null : weightedScore.divide(
                        scoredWeight, SCORE_SCALE, RoundingMode.HALF_UP);
        BigDecimal communication = assessment.communicationScore() == null
                ? null : scaled(BigDecimal.valueOf(assessment.communicationScore()));
        BigDecimal overall = null;
        // Không công bố overall score khi buổi phỏng vấn chưa thu đủ evidence tối thiểu.
        if (coverage.compareTo(properties.minimumCoveragePercentage()) >= 0
                && technical != null && communication != null) {
            overall = scaled(
                    technical.multiply(properties.technicalWeight())
                            .add(communication.multiply(properties.communicationWeight())));
        }

        return new InterviewScoreCalculation(
                technical,
                communication,
                overall,
                coverage,
                confidenceFor(coverage));
    }

    private BigDecimal priorityWeight(InterviewFocusPriority priority) {
        return BigDecimal.valueOf(switch (priority) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        });
    }

    private BigDecimal coverageFactor(InterviewEvidenceStatus status) {
        // Evidence PARTIAL chỉ đóng góp một nửa coverage của focus area.
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

    private InterviewAssessmentConfidence confidenceFor(BigDecimal coverage) {
        if (coverage.compareTo(properties.minimumCoveragePercentage()) < 0) {
            return InterviewAssessmentConfidence.LOW;
        }
        return coverage.compareTo(BigDecimal.valueOf(80)) >= 0
                ? InterviewAssessmentConfidence.HIGH
                : InterviewAssessmentConfidence.MEDIUM;
    }

    private BigDecimal scaled(BigDecimal value) {
        return value.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }
}
