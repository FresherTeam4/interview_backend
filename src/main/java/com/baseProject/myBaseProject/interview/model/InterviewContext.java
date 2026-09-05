package com.baseProject.myBaseProject.interview.model;

import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewerStyle;

import java.util.List;

public record InterviewContext(
        Long sessionId,
        String languageCode,
        int durationMinutes,
        InterviewerStyle interviewerStyle,
        String jobContextSummary,
        String candidateContextSummary,
        String openingMessage,
        String conversationSummary,
        int currentTurnIndex,
        List<FocusArea> focusAreas) {

    public InterviewContext {
        focusAreas = List.copyOf(focusAreas);
    }

    public record FocusArea(
            String code,
            String name,
            String description,
            InterviewFocusPriority priority,
            String reason,
            int plannedSeconds,
            InterviewEvidenceStatus evidenceStatus,
            String evidenceSummary,
            short displayOrder) {
    }
}
