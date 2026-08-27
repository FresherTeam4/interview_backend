package com.baseProject.myBaseProject.dto.interview;

import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionStatus;

public record TextAnswerAcceptedResponse(
        Long sessionId,
        Long candidateTurnId,
        SessionStatus status,
        AwaitingAction awaitingAction,
        long version) {
}
