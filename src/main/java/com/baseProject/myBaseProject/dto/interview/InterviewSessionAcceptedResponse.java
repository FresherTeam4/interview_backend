package com.baseProject.myBaseProject.dto.interview;

import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionStatus;

import java.time.Instant;

public record InterviewSessionAcceptedResponse(
        Long id,
        SessionStatus status,
        AwaitingAction awaitingAction,
        long version,
        Instant createdAt) {
}
