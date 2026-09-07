package com.baseProject.myBaseProject.dto.session;

import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;

import java.time.Instant;

public record InterviewAnswerResponse(
        Long sessionId,
        InterviewSessionStatus status,
        Instant deadlineAt,
        InterviewEndReason endReason,
        Instant endedAt,
        long remainingSeconds,
        int currentTurnIndex,
        InterviewTurnResponse candidateTurn,
        InterviewTurnResponse interviewerTurn) {
}
