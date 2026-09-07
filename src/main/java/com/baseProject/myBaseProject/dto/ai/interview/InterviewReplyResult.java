package com.baseProject.myBaseProject.dto.ai.interview;

import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;

import java.util.List;

public record InterviewReplyResult(
        InterviewTurnAction action,
        String interviewerMessage,
        String focusAreaCode,
        String conversationSummary,
        List<EvidenceUpdate> evidenceUpdates) {

    public record EvidenceUpdate(
            String focusAreaCode,
            InterviewEvidenceStatus status,
            String evidenceSummary) {
    }
}
