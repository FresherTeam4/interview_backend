package com.baseProject.myBaseProject.interview.model;

import com.baseProject.myBaseProject.enums.CandidateIntent;
import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;

import java.util.List;

public record InterviewScoringContext(
        Long sessionId,
        InterviewSessionStatus status,
        String languageCode,
        InterviewEndReason endReason,
        long actualDurationSeconds,
        InterviewTemplateSnapshot jobTemplate,
        String jobContextSummary,
        String conversationSummary,
        List<FocusArea> focusAreas,
        List<Turn> turns) {

    public InterviewScoringContext {
        focusAreas = focusAreas == null ? List.of() : List.copyOf(focusAreas);
        turns = turns == null ? List.of() : List.copyOf(turns);
    }

    public record FocusArea(
            Long id,
            String code,
            String name,
            String description,
            InterviewFocusPriority priority,
            String reason,
            InterviewEvidenceStatus evidenceStatus,
            String evidenceSummary) {
    }

    public record Turn(
            Long id,
            int turnIndex,
            InterviewTurnRole role,
            String content,
            CandidateIntent candidateIntent,
            InterviewTurnAction action,
            String focusAreaCode) {
    }
}
