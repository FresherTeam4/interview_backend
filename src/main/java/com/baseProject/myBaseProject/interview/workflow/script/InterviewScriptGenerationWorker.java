package com.baseProject.myBaseProject.interview.workflow.script;

import com.baseProject.myBaseProject.interview.generation.InterviewScriptGenerationService;

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
    private final InterviewScriptWorkCoordinator workCoordinator;

    public Optional<Instant> process(Long sessionId, UUID processingToken) {
        long startedNanos = System.nanoTime();
        String outcome = "ignored";
        try {
            InterviewScriptWorkCoordinator.ClaimInspection inspection =
                    workCoordinator.inspect(sessionId, processingToken);
            if (inspection == InterviewScriptWorkCoordinator.ClaimInspection.LOST) {
                return Optional.empty();
            }
            if (inspection == InterviewScriptWorkCoordinator.ClaimInspection.ATTEMPTS_EXHAUSTED) {
                outcome = "failed";
                return retryAt(workCoordinator.handleFailure(
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
            InterviewScriptWorkCoordinator.FailureOutcome failureOutcome =
                    workCoordinator.handleFailure(sessionId, processingToken, failure);
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
            InterviewScriptWorkCoordinator.FailureOutcome failureOutcome =
                    workCoordinator.handleFailure(
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
            InterviewScriptWorkCoordinator.FailureOutcome failureOutcome) {
        return Optional.ofNullable(failureOutcome.retryAt());
    }
}
