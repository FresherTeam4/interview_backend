package com.baseProject.myBaseProject.dto.interview;

import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.InterviewDifficulty;
import com.baseProject.myBaseProject.enums.SessionMode;
import com.baseProject.myBaseProject.enums.SessionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record InterviewSessionSummaryResponse(
        Long id,
        Long profileId,
        String profileHeadline,
        Long jobDescriptionId,
        String jobDescriptionTitle,
        InterviewDifficulty difficulty,
        SessionMode mode,
        SessionStatus status,
        AwaitingAction awaitingAction,
        short answeredQuestionCount,
        short totalQuestionCount,
        BigDecimal overallScore,
        Instant lastActivityAt,
        Instant createdAt) {
}
