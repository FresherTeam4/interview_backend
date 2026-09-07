package com.baseProject.myBaseProject.dto.session;

import com.baseProject.myBaseProject.enums.CandidateIntent;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnProcessingStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;

import java.time.Instant;

public record InterviewTurnResponse(
        Long id,
        int turnIndex,
        InterviewTurnRole role,
        String content,
        CandidateIntent candidateIntent,
        InterviewTurnAction action,
        String focusAreaCode,
        String requestId,
        InterviewTurnProcessingStatus processingStatus,
        String processingErrorCode,
        Instant createdAt) {
}
