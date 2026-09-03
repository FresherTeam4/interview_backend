package com.baseProject.myBaseProject.interview.workflow;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionFailureStage;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.exception.FollowUpDecisionException;
import com.baseProject.myBaseProject.exception.ScriptGenerationException;
import com.baseProject.myBaseProject.interview.lifecycle.SessionStateMachine;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Owns the retry and terminal-failure policy shared by interview AI workflows. */
@Service
@RequiredArgsConstructor
public class InterviewWorkflowCoordinator {

    private static final short SCRIPT_MAX_ATTEMPTS = 2;
    private static final short NEXT_TURN_MAX_ATTEMPTS = 3;

    private final InterviewSessionRepository sessionRepository;
    private final SessionProcessingClaimService claimService;
    private final SessionStateMachine stateMachine;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ClaimInspection inspectScript(Long sessionId, UUID processingToken) {
        return inspect(
                sessionId,
                processingToken,
                SessionProcessingStage.SCRIPT_GENERATION,
                SCRIPT_MAX_ATTEMPTS);
    }

    @Transactional(readOnly = true)
    public ClaimInspection inspectNextTurn(Long sessionId, UUID processingToken) {
        return inspect(
                sessionId,
                processingToken,
                SessionProcessingStage.NEXT_TURN,
                NEXT_TURN_MAX_ATTEMPTS);
    }

    @Transactional
    public FailureOutcome handleScriptFailure(
            Long sessionId,
            UUID processingToken,
            ScriptGenerationException failure) {
        return handleFailure(
                sessionId,
                processingToken,
                SessionProcessingStage.SCRIPT_GENERATION,
                SessionFailureStage.SCRIPT_GENERATION,
                SCRIPT_MAX_ATTEMPTS,
                failure.isRetryable(),
                failure.getStatusMessage(),
                failure.getReason().name());
    }

    @Transactional
    public FailureOutcome handleNextTurnFailure(
            Long sessionId,
            UUID processingToken,
            FollowUpDecisionException failure) {
        return handleFailure(
                sessionId,
                processingToken,
                SessionProcessingStage.NEXT_TURN,
                SessionFailureStage.NEXT_TURN,
                NEXT_TURN_MAX_ATTEMPTS,
                failure.isRetryable(),
                failure.getStatusMessage(),
                failure.getReason().name());
    }

    private ClaimInspection inspect(
            Long sessionId,
            UUID processingToken,
            SessionProcessingStage stage,
            short maxAttempts) {
        InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (!ownsClaim(session, stage, processingToken)) {
            return ClaimInspection.LOST;
        }
        return session.getProcessingAttempts() <= maxAttempts
                ? ClaimInspection.RUNNABLE
                : ClaimInspection.ATTEMPTS_EXHAUSTED;
    }

    private FailureOutcome handleFailure(
            Long sessionId,
            UUID processingToken,
            SessionProcessingStage stage,
            SessionFailureStage failureStage,
            short maxAttempts,
            boolean retryable,
            String statusMessage,
            String reason) {
        InterviewSession session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (!ownsClaim(session, stage, processingToken)) {
            return FailureOutcome.lostClaim();
        }

        if (retryable && session.getProcessingAttempts() < maxAttempts) {
            Instant retryAt = clock.instant().plus(
                    retryBackoffSeconds(stage, session.getProcessingAttempts()),
                    ChronoUnit.SECONDS);
            boolean released = stage == SessionProcessingStage.NEXT_TURN
                    ? claimService.releaseNextTurnForRetry(
                            sessionId,
                            processingToken,
                            retryAt,
                            statusMessage)
                    : claimService.releaseForRetry(
                            sessionId,
                            stage,
                            processingToken,
                            retryAt,
                            statusMessage);
            return released
                    ? FailureOutcome.retryAt(retryAt)
                    : FailureOutcome.lostClaim();
        }

        stateMachine.failWorkflow(
                sessionId,
                session.getVersion(),
                processingToken,
                failureStage,
                statusMessage,
                workflowName(stage) + " failed: " + reason);
        return FailureOutcome.terminalFailure();
    }

    private boolean ownsClaim(
            InterviewSession session,
            SessionProcessingStage stage,
            UUID processingToken) {
        if (session == null
                || processingToken == null
                || session.getProcessingStage() != stage
                || !processingToken.toString().equals(session.getProcessingToken())) {
            return false;
        }
        return switch (stage) {
            case SCRIPT_GENERATION -> session.getStatus() == SessionStatus.SCRIPT_GENERATING;
            case NEXT_TURN -> session.getStatus() == SessionStatus.IN_PROGRESS
                    && (session.getAwaitingAction() == AwaitingAction.ENGINE_RESPONSE
                            || session.getAwaitingAction() == AwaitingAction.ENGINE_RETRY);
            case SCORING -> session.getStatus() == SessionStatus.SCORING;
        };
    }

    private long retryBackoffSeconds(
            SessionProcessingStage stage,
            short attempts) {
        if (stage == SessionProcessingStage.SCRIPT_GENERATION || attempts == 1) {
            return 1;
        }
        return 3;
    }

    private String workflowName(SessionProcessingStage stage) {
        return switch (stage) {
            case SCRIPT_GENERATION -> "Script generation";
            case NEXT_TURN -> "Next-turn decision";
            case SCORING -> "Scoring";
        };
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
