package com.baseProject.myBaseProject.interview.workflow.turn;

import com.baseProject.myBaseProject.exception.FollowUpDecisionException;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.interview.turn.InterviewAdaptiveNextTurnService;
import com.baseProject.myBaseProject.interview.turn.model.NextTurnData.NextTurnOutcome;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowCoordinator;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowDispatcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
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
    private final InterviewWorkflowCoordinator workflowCoordinator;
    private final ObjectProvider<InterviewWorkflowDispatcher> workflowDispatcherProvider;

    public Optional<Instant> process(Long sessionId, UUID processingToken) {
        long startedNanos = System.nanoTime();
        String outcome = "ignored";
        try {
            InterviewWorkflowCoordinator.ClaimInspection inspection =
                    workflowCoordinator.inspectNextTurn(sessionId, processingToken);
            if (inspection == InterviewWorkflowCoordinator.ClaimInspection.LOST) {
                return Optional.empty();
            }
            if (inspection == InterviewWorkflowCoordinator.ClaimInspection.ATTEMPTS_EXHAUSTED) {
                outcome = "failed";
                return retryAt(workflowCoordinator.handleNextTurnFailure(
                        sessionId,
                        processingToken,
                        FollowUpDecisionException.unexpected(
                                new IllegalStateException(
                                        "Next-turn provider attempt limit was already exhausted"))));
            }

            NextTurnOutcome result =
                    nextTurnService.decideAndPersist(sessionId, processingToken);
            outcome = result.name().toLowerCase();
            if (result == NextTurnOutcome.SCORING) {
                workflowDispatcherProvider.getObject().claimAndDispatch(
                        sessionId,
                        SessionProcessingStage.SCORING);
            }
            return Optional.empty();
        } catch (FollowUpDecisionException failure) {
            InterviewWorkflowCoordinator.FailureOutcome failureOutcome =
                    workflowCoordinator.handleNextTurnFailure(sessionId, processingToken, failure);
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
        } catch (RuntimeException unexpected) {
            InterviewWorkflowCoordinator.FailureOutcome failureOutcome =
                    workflowCoordinator.handleNextTurnFailure(
                            sessionId,
                            processingToken,
                            FollowUpDecisionException.unexpected(unexpected));
            outcome = failureOutcome.ignored() ? "ignored" : "failed";
            log.error(
                    "Unexpected interview next-turn failure: "
                            + "sessionId={}, exceptionType={}, outcome={}",
                    sessionId,
                    unexpected.getClass().getSimpleName(),
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

    private Optional<Instant> retryAt(InterviewWorkflowCoordinator.FailureOutcome outcome) {
        return Optional.ofNullable(outcome.retryAt());
    }
}
