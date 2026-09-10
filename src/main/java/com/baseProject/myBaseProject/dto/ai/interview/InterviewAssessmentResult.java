package com.baseProject.myBaseProject.dto.ai.interview;

import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;

import java.util.List;

public record InterviewAssessmentResult(
        String overallSummary,
        String technicalFeedback,
        List<FocusAreaAssessment> focusAreaAssessments,
        Integer communicationScore,
        String communicationFeedback,
        List<String> recommendations) {

    public record FocusAreaAssessment(
            String focusAreaCode,
            Integer score,
            InterviewEvidenceStatus evidenceStatus,
            List<Long> evidenceTurnIds) {
    }
}
