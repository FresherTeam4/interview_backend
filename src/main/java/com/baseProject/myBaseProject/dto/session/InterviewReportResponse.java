package com.baseProject.myBaseProject.dto.session;

import com.baseProject.myBaseProject.enums.InterviewSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InterviewReportResponse(
        Long sessionId,
        InterviewSessionStatus status,
        String scoringErrorCode,
        String scoringErrorMessage,
        Report report,
        Instant completedAt) {

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record Report(
            BigDecimal score,
            String summary,
            ScoreBreakdown scores,
            List<FocusAreaScore> focusAreas,
            List<String> recommendations) {

        public Report {
            focusAreas = safe(focusAreas);
            recommendations = safe(recommendations);
        }
    }

    public record ScoreBreakdown(
            ScoreFeedback technical,
            ScoreFeedback communication) {
    }

    public record ScoreFeedback(
            BigDecimal score,
            String feedback) {
    }

    public record FocusAreaScore(
            String name,
            BigDecimal score) {
    }
}
