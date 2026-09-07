package com.baseProject.myBaseProject.dto.ai.interview;

import com.baseProject.myBaseProject.enums.InterviewAssessmentConfidence;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;

import java.util.List;

public record InterviewAssessmentResult(
        String overallSummary,
        List<FocusAreaAssessment> focusAreaAssessments,
        Integer communicationScore,
        String communicationFeedback,
        List<ReportItem> strengths,
        List<ReportItem> improvements,
        List<ActionPlanItem> actionPlan) {

    public record FocusAreaAssessment(
            String focusAreaCode,
            Integer score,
            InterviewAssessmentConfidence confidence,
            InterviewEvidenceStatus evidenceStatus,
            String rationale,
            List<String> strengths,
            List<String> gaps,
            String feedback,
            List<Long> evidenceTurnIds) {
    }

    public record ReportItem(
            String title,
            String description,
            List<Long> evidenceTurnIds) {
    }

    public record ActionPlanItem(
            Integer priority,
            String action,
            String reason,
            String suggestion) {
    }
}
