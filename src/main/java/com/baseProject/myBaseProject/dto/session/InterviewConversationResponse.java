package com.baseProject.myBaseProject.dto.session;

import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;

import java.time.Instant;
import java.util.List;

public record InterviewConversationResponse(
        Long sessionId,
        InterviewSessionStatus status,
        InterviewSessionMode mode,
        String realtimeProvider,
        String realtimeVoiceName,
        Instant startedAt,
        Instant deadlineAt,
        InterviewEndReason endReason,
        Instant endedAt,
        long remainingSeconds,
        int currentTurnIndex,
        List<InterviewTurnResponse> turns) {

    public InterviewConversationResponse {
        turns = turns == null ? List.of() : List.copyOf(turns);
    }
}
