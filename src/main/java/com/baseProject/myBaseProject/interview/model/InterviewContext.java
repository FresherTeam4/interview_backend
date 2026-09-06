package com.baseProject.myBaseProject.interview.model;

import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewerStyle;

import java.time.Instant;
import java.util.List;

public record InterviewContext(
        Long sessionId,
        InterviewSessionStatus status,
        String languageCode,
        int durationMinutes,
        InterviewerStyle interviewerStyle,
        InterviewTemplateSnapshot template,
        CandidateProfileSnapshot candidate,
        String jobContextSummary,
        String candidateContextSummary,
        String openingMessage,
        String conversationSummary,
        Instant startedAt,
        Instant deadlineAt,
        int currentTurnIndex,
        List<FocusArea> focusAreas) {

    public InterviewContext {
        focusAreas = focusAreas == null ? List.of() : List.copyOf(focusAreas);
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
