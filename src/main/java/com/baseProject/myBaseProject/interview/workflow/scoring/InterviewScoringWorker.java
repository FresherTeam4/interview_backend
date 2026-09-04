package com.baseProject.myBaseProject.interview.workflow.scoring;

import com.baseProject.myBaseProject.exception.InterviewScoringException;
import com.baseProject.myBaseProject.interview.scoring.InterviewScoringService;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowCoordinator;

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
public class InterviewScoringWorker {

    private final InterviewScoringService scoringService;
    private final InterviewWorkflowCoordinator workflowCoordinator;

    public Optional<Instant> process(Long sessionId, UUID processingToken) {
        long startedNanos = System.nanoTime();
        String outcome = "ignored";
        try {
            InterviewWorkflowCoordinator.ClaimInspection inspection =
                    workflowCoordinator.inspectScoring(sessionId, processingToken);
            if (inspection == InterviewWorkflowCoordinator.ClaimInspection.LOST) {
                return Optional.empty();
            }
            if (inspection == InterviewWorkflowCoordinator.ClaimInspection.ATTEMPTS_EXHAUSTED) {
                outcome = "failed";
                return retryAt(workflowCoordinator.handleScoringFailure(
                        sessionId,
                        processingToken,
                        InterviewScoringException.unexpected(
                                new IllegalStateException(
                                        "Scoring provider attempt limit was already exhausted"))));
            }

            boolean committed = scoringService.scoreAndPersist(sessionId, processingToken);
            outcome = committed ? "completed" : "ignored";
            return Optional.empty();
        } catch (InterviewScoringException failure) {
            InterviewWorkflowCoordinator.FailureOutcome failureOutcome =
                    workflowCoordinator.handleScoringFailure(
                            sessionId,
                            processingToken,
                            failure);
            outcome = failureOutcome.ignored()
                    ? "ignored"
                    : failureOutcome.failed() ? "failed" : "retry_scheduled";
            log.warn(
                    "Interview scoring attempt ended: sessionId={}, reason={}, "
                            + "retryable={}, outcome={}",
                    sessionId,
                    failure.getReason(),
                    failure.isRetryable(),
                    outcome);
            return retryAt(failureOutcome);
        } catch (RuntimeException unexpected) {
            InterviewWorkflowCoordinator.FailureOutcome failureOutcome =
                    workflowCoordinator.handleScoringFailure(
                            sessionId,
                            processingToken,
                            InterviewScoringException.unexpected(unexpected));
            outcome = failureOutcome.ignored() ? "ignored" : "failed";
            log.error(
                    "Unexpected interview scoring failure: sessionId={}, "
                            + "exceptionType={}, outcome={}",
                    sessionId,
                    unexpected.getClass().getSimpleName(),
                    outcome);
            return retryAt(failureOutcome);
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            log.info(
                    "Interview scoring worker finished: sessionId={}, outcome={}, durationMs={}",
                    sessionId,
                    outcome,
                    durationMs);
        }
    }

    private Optional<Instant> retryAt(InterviewWorkflowCoordinator.FailureOutcome outcome) {
        return Optional.ofNullable(outcome.retryAt());
    }
}
