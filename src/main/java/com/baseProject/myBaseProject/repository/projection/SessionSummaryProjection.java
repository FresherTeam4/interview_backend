package com.baseProject.myBaseProject.repository.projection;

import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.InterviewDifficulty;
import com.baseProject.myBaseProject.enums.SessionMode;
import com.baseProject.myBaseProject.enums.SessionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record SessionSummaryProjection(
        Long id,
        InterviewDifficulty difficulty,
        SessionMode mode,
        SessionStatus status,
        AwaitingAction awaitingAction,
        short answeredQuestionCount,
        short totalQuestionCount,
        BigDecimal overallScore,
        Instant lastActivityAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {
}
