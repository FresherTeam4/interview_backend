package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionFailureStage;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.exception.FollowUpDecisionException;
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
public class InterviewNextTurnWorkCoordinator {

    private static final short MAX_PROVIDER_ATTEMPTS = 3;

    private final InterviewSessionRepository sessionRepository;
    private final SessionProcessingClaimService claimService;
    private final SessionStateMachine stateMachine;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ClaimInspection inspect(Long sessionId, UUID processingToken) {
        InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (!ownsNextTurnClaim(session, processingToken)) {
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
            FollowUpDecisionException failure) {
        InterviewSession session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (!ownsNextTurnClaim(session, processingToken)) {
            return FailureOutcome.lostClaim();
        }

        if (failure.isRetryable()
                && session.getProcessingAttempts() < MAX_PROVIDER_ATTEMPTS) {
            long backoffSeconds = session.getProcessingAttempts() == 1 ? 1 : 3;
            Instant retryAt = clock.instant().plus(backoffSeconds, ChronoUnit.SECONDS);
            boolean released = claimService.releaseNextTurnForRetry(
                    sessionId,
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
                SessionFailureStage.NEXT_TURN,
                failure.getStatusMessage(),
                "Next-turn decision failed: " + failure.getReason().name());
        return FailureOutcome.terminalFailure();
    }

    private boolean ownsNextTurnClaim(
            InterviewSession session,
            UUID processingToken) {
        return session != null
                && session.getStatus() == SessionStatus.IN_PROGRESS
                && (session.getAwaitingAction() == AwaitingAction.ENGINE_RESPONSE
                        || session.getAwaitingAction() == AwaitingAction.ENGINE_RETRY)
                && session.getProcessingStage() == SessionProcessingStage.NEXT_TURN
                && processingToken != null
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
