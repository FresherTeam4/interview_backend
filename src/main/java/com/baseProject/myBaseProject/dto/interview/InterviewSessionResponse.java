package com.baseProject.myBaseProject.dto.interview;

import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.InterviewDifficulty;
import com.baseProject.myBaseProject.enums.SessionMode;
import com.baseProject.myBaseProject.enums.SessionStatus;

import java.time.Instant;

public record InterviewSessionResponse(
        Long id,
        ProfileReference profile,
        JobDescriptionReference jobDescription,
        InterviewDifficulty difficulty,
        SessionMode mode,
        String languageCode,
        SessionStatus status,
        AwaitingAction awaitingAction,
        long version,
        short answeredQuestionCount,
        short totalQuestionCount,
        String statusMessage,
        Instant lastActivityAt,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {

    public record ProfileReference(Long id, String headline) {
    }

    public record JobDescriptionReference(Long id, String title) {
    }
}
