package com.baseProject.myBaseProject.scheduler;

import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.interview.InterviewScriptWorkflowDispatcher;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewWorkflowRecoveryJob {

    private static final int RECOVERY_BATCH_SIZE = 50;

    private final InterviewProperties properties;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewScriptWorkflowDispatcher workflowDispatcher;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverWhenApplicationIsReady() {
        recoverScriptGeneration();
    }

    @Scheduled(cron = "${app.interview.recovery-cron}")
    public void recoverPeriodically() {
        recoverScriptGeneration();
    }

    private void recoverScriptGeneration() {
        if (!properties.enabled()) {
            return;
        }
        Instant now = clock.instant();
        try {
            List<Long> sessionIds = sessionRepository.findRecoverableWorkIds(
                    List.of(SessionStatus.SCRIPT_GENERATING),
                    List.of(SessionProcessingStage.SCRIPT_GENERATION),
                    now,
                    now.minus(properties.processingLease()),
                    PageRequest.of(0, RECOVERY_BATCH_SIZE));
            int claimed = 0;
            for (Long sessionId : sessionIds) {
                if (workflowDispatcher.claimAndDispatch(sessionId)) {
                    claimed++;
                }
            }
            if (!sessionIds.isEmpty()) {
                log.info(
                        "Interview script recovery scanned: candidates={}, claimed={}",
                        sessionIds.size(),
                        claimed);
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Interview script recovery failed: exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
