package com.baseProject.myBaseProject.interview.workflow.script;

import com.baseProject.myBaseProject.interview.generation.InterviewScriptGenerationService;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowCoordinator;

import com.baseProject.myBaseProject.exception.ScriptGenerationException;

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
public class InterviewScriptGenerationWorker {

    private final InterviewScriptGenerationService generationService;
    private final InterviewWorkflowCoordinator workflowCoordinator;

    public Optional<Instant> process(Long sessionId, UUID processingToken) {
        long startedNanos = System.nanoTime();
        String outcome = "ignored";
        try {
            InterviewWorkflowCoordinator.ClaimInspection inspection =
                    workflowCoordinator.inspectScript(sessionId, processingToken);
            if (inspection == InterviewWorkflowCoordinator.ClaimInspection.LOST) {
                return Optional.empty();
            }
            if (inspection == InterviewWorkflowCoordinator.ClaimInspection.ATTEMPTS_EXHAUSTED) {
                outcome = "failed";
                return retryAt(workflowCoordinator.handleScriptFailure(
                        sessionId,
                        processingToken,
                        ScriptGenerationException.unexpected(
                                new IllegalStateException(
                                        "Script provider attempt limit was already exhausted"))));
            }

            generationService.generateAndPersist(sessionId, processingToken);
            outcome = "ready";
            return Optional.empty();
        } catch (ScriptGenerationException failure) {
            InterviewWorkflowCoordinator.FailureOutcome failureOutcome =
                    workflowCoordinator.handleScriptFailure(sessionId, processingToken, failure);
            outcome = failureOutcome.ignored()
                    ? "ignored"
                    : failureOutcome.failed() ? "failed" : "retry_scheduled";
            log.warn(
                    "Interview script attempt ended: sessionId={}, reason={}, retryable={}, outcome={}",
                    sessionId,
                    failure.getReason(),
                    failure.isRetryable(),
                    outcome);
            return retryAt(failureOutcome);
        } catch (RuntimeException unexpected) {
            InterviewWorkflowCoordinator.FailureOutcome failureOutcome =
                    workflowCoordinator.handleScriptFailure(
                            sessionId,
                            processingToken,
                            ScriptGenerationException.unexpected(unexpected));
            outcome = failureOutcome.ignored() ? "ignored" : "failed";
            log.error(
                    "Unexpected interview script failure: sessionId={}, exceptionType={}, outcome={}",
                    sessionId,
                    unexpected.getClass().getSimpleName(),
                    outcome);
            return retryAt(failureOutcome);
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            log.info(
                    "Interview script workflow finished: sessionId={}, outcome={}, durationMs={}",
                    sessionId,
                    outcome,
                    durationMs);
        }
    }

    private Optional<Instant> retryAt(
            InterviewWorkflowCoordinator.FailureOutcome failureOutcome) {
        return Optional.ofNullable(failureOutcome.retryAt());
    }
}
