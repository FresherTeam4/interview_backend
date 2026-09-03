package com.baseProject.myBaseProject.interview.workflow;

import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionProcessingClaimService {

    private static final int STATUS_MESSAGE_MAX_LENGTH = 500;

    private final InterviewSessionRepository sessionRepository;
    private final Clock clock;

    @Transactional
    public boolean claim(
            Long sessionId,
            SessionProcessingStage stage,
            UUID token,
            Duration staleAfter) {
        if (staleAfter == null || staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException("Claim stale duration must be positive");
        }
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(stage);
        Objects.requireNonNull(token);
        Instant now = clock.instant();
        return sessionRepository.claimProcessing(
                        sessionId,
                        stage,
                        token.toString(),
                        now,
                        now.minus(staleAfter))
                == 1;
    }

    @Transactional
    public boolean releaseForRetry(
            Long sessionId,
            SessionProcessingStage stage,
            UUID token,
            Instant nextRetryAt,
            String statusMessage) {
        Instant now = clock.instant();
        if (nextRetryAt != null && nextRetryAt.isBefore(now)) {
            throw new IllegalArgumentException("Next retry must not be in the past");
        }
        return sessionRepository.releaseProcessingClaimForRetry(
                        sessionId,
                        stage,
                        token.toString(),
                        nextRetryAt,
                        normalizeStatusMessage(statusMessage),
                        now)
                == 1;
    }

    @Transactional
    public boolean releaseNextTurnForRetry(
            Long sessionId,
            UUID token,
            Instant nextRetryAt,
            String statusMessage) {
        Instant now = clock.instant();
        if (nextRetryAt == null || nextRetryAt.isBefore(now)) {
            throw new IllegalArgumentException("Next-turn retry must be scheduled in the future");
        }
        return sessionRepository.releaseNextTurnProcessingClaimForRetry(
                        sessionId,
                        SessionProcessingStage.NEXT_TURN,
                        token.toString(),
                        AwaitingAction.ENGINE_RETRY,
                        nextRetryAt,
                        normalizeStatusMessage(statusMessage),
                        now)
                == 1;
    }

    private String normalizeStatusMessage(String statusMessage) {
        if (statusMessage == null || statusMessage.isBlank()) {
            return null;
        }
        String normalized = statusMessage.strip();
        return normalized.length() <= STATUS_MESSAGE_MAX_LENGTH
                ? normalized
                : normalized.substring(0, STATUS_MESSAGE_MAX_LENGTH);
    }
}
