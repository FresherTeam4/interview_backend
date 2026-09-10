package com.baseProject.myBaseProject.dto.session;

import com.baseProject.myBaseProject.enums.InterviewAssessmentConfidence;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InterviewReportResponse(
        Long sessionId,
        InterviewSessionStatus status,
        String scoringErrorCode,
        String scoringErrorMessage,
        BigDecimal technicalScore,
        BigDecimal communicationScore,
        BigDecimal overallScore,
        BigDecimal coveragePercentage,
        InterviewAssessmentConfidence confidence,
        String overallSummary,
        List<ImprovementItem> improvements,
        String communicationFeedback,
        List<FocusAreaResult> focusAreas,
        Instant completedAt) {

    public InterviewReportResponse {
        improvements = safe(improvements);
        focusAreas = safe(focusAreas);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record ImprovementItem(
            String title,
            String summary) {
    }

    public record FocusAreaResult(
            Long focusAreaId,
            String code,
            String name,
            InterviewFocusPriority priority,
            short displayOrder,
            BigDecimal score,
            InterviewAssessmentConfidence confidence,
            InterviewEvidenceStatus evidenceStatus,
            String summary) {
    }
}
