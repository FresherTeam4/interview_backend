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
        List<ReportItem> strengths,
        List<ReportItem> improvements,
        List<ActionPlanItem> actionPlan,
        String communicationFeedback,
        List<FocusAreaResult> focusAreas,
        Instant completedAt) {

    public InterviewReportResponse {
        strengths = safe(strengths);
        improvements = safe(improvements);
        actionPlan = safe(actionPlan);
        focusAreas = safe(focusAreas);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record ReportItem(
            String title,
            String description,
            List<Long> evidenceTurnIds) {

        public ReportItem {
            evidenceTurnIds = safe(evidenceTurnIds);
        }
    }

    public record ActionPlanItem(
            int priority,
            String action,
            String reason,
            String suggestion) {
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
            String rationale,
            List<String> strengths,
            List<String> gaps,
            String feedback,
            List<Long> evidenceTurnIds) {

        public FocusAreaResult {
            strengths = safe(strengths);
            gaps = safe(gaps);
            evidenceTurnIds = safe(evidenceTurnIds);
        }
    }
}
