package com.baseProject.myBaseProject.dto.interview;

import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.InterviewDifficulty;
import com.baseProject.myBaseProject.enums.SessionMode;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnInputMode;
import com.baseProject.myBaseProject.enums.TurnRole;

import java.time.Instant;
import java.util.List;

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
        CurrentPrompt currentPrompt,
        List<Turn> turns,
        VoiceAttemptResponse voiceDraft,
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

    public record CurrentPrompt(
            Long turnId,
            Long baseQuestionId,
            short ordinal,
            String text,
            boolean isFollowUp,
            short followUpDepth,
            String audioStatus) {
    }

    public record Turn(
            Long id,
            int turnIndex,
            TurnRole role,
            TurnInputMode inputMode,
            String content,
            boolean isFollowUp,
            short followUpDepth,
            Instant createdAt) {
    }
}
