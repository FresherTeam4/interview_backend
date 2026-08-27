package com.baseProject.myBaseProject.interview;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewNextTurnWorker {

    private final InterviewBaseQuestionProgressionService progressionService;

    public void process(Long sessionId, UUID processingToken) {
        long startedNanos = System.nanoTime();
        String outcome = "failed";
        try {
            InterviewBaseQuestionProgressionService.ProgressionOutcome result =
                    progressionService.progress(sessionId, processingToken);
            outcome = result.name().toLowerCase();
        } catch (RuntimeException exception) {
            log.error(
                    "Interview next-turn workflow failed; recovery will retry: "
                            + "sessionId={}, exceptionType={}",
                    sessionId,
                    exception.getClass().getSimpleName());
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            log.info(
                    "Interview next-turn workflow finished: "
                            + "sessionId={}, outcome={}, durationMs={}",
                    sessionId,
                    outcome,
                    durationMs);
        }
    }
}
