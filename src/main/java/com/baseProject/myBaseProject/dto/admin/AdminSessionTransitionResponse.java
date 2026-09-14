package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTransitionActor;

import java.time.Instant;

public record AdminSessionTransitionResponse(
        Long id,
        InterviewSessionStatus fromStatus,
        InterviewSessionStatus toStatus,
        String reason,
        InterviewTransitionActor actor,
        Instant occurredAt) {
}
