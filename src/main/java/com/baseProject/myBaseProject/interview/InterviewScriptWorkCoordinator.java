package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.SessionFailureStage;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.exception.ScriptGenerationException;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewScriptWorkCoordinator {

    private static final short MAX_PROVIDER_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_SECONDS = 1;

    private final InterviewSessionRepository sessionRepository;
    private final SessionProcessingClaimService claimService;
    private final SessionStateMachine stateMachine;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ClaimInspection inspect(Long sessionId, UUID processingToken) {
        InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (!ownsScriptClaim(session, processingToken)) {
            return ClaimInspection.LOST;
        }
        return session.getProcessingAttempts() <= MAX_PROVIDER_ATTEMPTS
                ? ClaimInspection.RUNNABLE
                : ClaimInspection.ATTEMPTS_EXHAUSTED;
    }

    @Transactional
    public FailureOutcome handleFailure(
            Long sessionId,
            UUID processingToken,
            ScriptGenerationException failure) {
        InterviewSession session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (!ownsScriptClaim(session, processingToken)) {
            return FailureOutcome.lostClaim();
        }

        if (failure.isRetryable()
                && session.getProcessingAttempts() < MAX_PROVIDER_ATTEMPTS) {
            Instant retryAt = clock.instant().plus(RETRY_BACKOFF_SECONDS, ChronoUnit.SECONDS);
            boolean released = claimService.releaseForRetry(
                    sessionId,
                    SessionProcessingStage.SCRIPT_GENERATION,
                    processingToken,
                    retryAt,
                    failure.getStatusMessage());
            return released
                    ? FailureOutcome.retryAt(retryAt)
                    : FailureOutcome.lostClaim();
        }

        stateMachine.transitionSystem(
                sessionId,
                session.getVersion(),
                SessionEvent.WORKFLOW_FAILED,
                processingToken,
                SessionFailureStage.SCRIPT_GENERATION,
                failure.getStatusMessage(),
                "Script generation failed: " + failure.getReason().name());
        return FailureOutcome.terminalFailure();
    }

    private boolean ownsScriptClaim(InterviewSession session, UUID processingToken) {
        return session != null
                && session.getStatus() == SessionStatus.SCRIPT_GENERATING
                && session.getProcessingStage() == SessionProcessingStage.SCRIPT_GENERATION
                && processingToken.toString().equals(session.getProcessingToken());
    }

    public enum ClaimInspection {
        RUNNABLE,
        ATTEMPTS_EXHAUSTED,
        LOST
    }

    public record FailureOutcome(Instant retryAt, boolean failed, boolean ignored) {

        public static FailureOutcome retryAt(Instant retryAt) {
            return new FailureOutcome(retryAt, false, false);
        }

        public static FailureOutcome terminalFailure() {
            return new FailureOutcome(null, true, false);
        }

        public static FailureOutcome lostClaim() {
            return new FailureOutcome(null, false, true);
        }
    }
}
