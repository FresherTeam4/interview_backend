package com.baseProject.myBaseProject.dto.interview;

import com.baseProject.myBaseProject.enums.VoiceAttemptStatus;

import java.time.Instant;

public record VoiceAttemptResponse(
        Long id,
        Long promptTurnId,
        short attemptNo,
        VoiceAttemptStatus status,
        long version,
        String rawText,
        String editedText,
        int durationMs,
        String statusMessage,
        Instant createdAt) {
}
