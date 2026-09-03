package com.baseProject.myBaseProject.interview.workflow.turn;

import com.baseProject.myBaseProject.exception.FollowUpDecisionException;
import com.baseProject.myBaseProject.interview.turn.InterviewAdaptiveNextTurnService;
import com.baseProject.myBaseProject.interview.turn.model.NextTurnOutcome;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewNextTurnWorker {

    private final InterviewAdaptiveNextTurnService nextTurnService;
    private final InterviewNextTurnWorkCoordinator workCoordinator;

    public Optional<Instant> process(Long sessionId, UUID processingToken) {
        long startedNanos = System.nanoTime();
        String outcome = "ignored";
        try {
            InterviewNextTurnWorkCoordinator.ClaimInspection inspection =
                    workCoordinator.inspect(sessionId, processingToken);
            if (inspection == InterviewNextTurnWorkCoordinator.ClaimInspection.LOST) {
                return Optional.empty();
            }
            if (inspection == InterviewNextTurnWorkCoordinator.ClaimInspection.ATTEMPTS_EXHAUSTED) {
                outcome = "failed";
                return retryAt(workCoordinator.handleFailure(
                        sessionId,
                        processingToken,
                        FollowUpDecisionException.unexpected(
                                new IllegalStateException(
                                        "Next-turn provider attempt limit was already exhausted"))));
            }

            NextTurnOutcome result =
                    nextTurnService.decideAndPersist(sessionId, processingToken);
            outcome = result.name().toLowerCase();
            return Optional.empty();
        } catch (FollowUpDecisionException failure) {
            InterviewNextTurnWorkCoordinator.FailureOutcome failureOutcome =
                    workCoordinator.handleFailure(sessionId, processingToken, failure);
            outcome = failureOutcome.ignored()
                    ? "ignored"
                    : failureOutcome.failed() ? "failed" : "retry_scheduled";
            log.warn(
                    "Interview next-turn attempt ended: "
                            + "sessionId={}, reason={}, retryable={}, outcome={}",
                    sessionId,
                    failure.getReason(),
                    failure.isRetryable(),
                    outcome);
            return retryAt(failureOutcome);
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            log.info(
                    "Interview next-turn worker execution finished: "
                            + "sessionId={}, outcome={}, durationMs={}",
                    sessionId,
                    outcome,
                    durationMs);
        }
    }

    private Optional<Instant> retryAt(InterviewNextTurnWorkCoordinator.FailureOutcome outcome) {
        return Optional.ofNullable(outcome.retryAt());
    }
}
