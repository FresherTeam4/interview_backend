package com.baseProject.myBaseProject.dto.ai.interview;

import com.baseProject.myBaseProject.enums.InterviewFocusPriority;

import java.util.List;

public record InterviewPlanResult(
        String languageCode,
        String jobContextSummary,
        String candidateContextSummary,
        List<FocusArea> focusAreas,
        String openingMessage) {

    public record FocusArea(
            String code,
            String name,
            String description,
            InterviewFocusPriority priority,
            String reason,
            Integer plannedSeconds) {
    }
}
