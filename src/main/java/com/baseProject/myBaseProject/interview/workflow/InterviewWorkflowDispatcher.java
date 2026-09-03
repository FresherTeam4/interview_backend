package com.baseProject.myBaseProject.interview.workflow;

import com.baseProject.myBaseProject.config.AsyncConfig;
import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.interview.workflow.script.InterviewScriptGenerationWorker;
import com.baseProject.myBaseProject.interview.workflow.turn.InterviewNextTurnWorker;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class InterviewWorkflowDispatcher {

    private final InterviewProperties properties;
    private final SessionProcessingClaimService claimService;
    private final InterviewScriptGenerationWorker scriptWorker;
    private final InterviewNextTurnWorker nextTurnWorker;
    private final ThreadPoolTaskExecutor executor;
    private final TaskScheduler scheduler;

    public InterviewWorkflowDispatcher(
            InterviewProperties properties,
            SessionProcessingClaimService claimService,
            InterviewScriptGenerationWorker scriptWorker,
            InterviewNextTurnWorker nextTurnWorker,
            @Qualifier(AsyncConfig.INTERVIEW_AI_EXECUTOR) ThreadPoolTaskExecutor executor,
            @Qualifier(AsyncConfig.INTERVIEW_WORKFLOW_SCHEDULER) TaskScheduler scheduler) {
        this.properties = properties;
        this.claimService = claimService;
        this.scriptWorker = scriptWorker;
        this.nextTurnWorker = nextTurnWorker;
        this.executor = executor;
        this.scheduler = scheduler;
    }

    public void dispatchAfterCommit(
            Long sessionId,
            SessionProcessingStage stage,
            UUID processingToken) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatchClaimed(sessionId, stage, processingToken);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchClaimed(sessionId, stage, processingToken);
            }
        });
    }

    public boolean claimAndDispatch(Long sessionId, SessionProcessingStage stage) {
        if (!properties.enabled() || stage == SessionProcessingStage.SCORING) {
            return false;
        }
        UUID processingToken = UUID.randomUUID();
        boolean claimed = claimService.claim(
                sessionId,
                stage,
                processingToken,
                properties.processingLease());
        if (claimed) {
            dispatchClaimed(sessionId, stage, processingToken);
        }
        return claimed;
    }

    private void dispatchClaimed(
            Long sessionId,
            SessionProcessingStage stage,
            UUID processingToken) {
        if (!properties.enabled()) {
            return;
        }
        try {
            executor.execute(() -> runClaimed(sessionId, stage, processingToken));
        } catch (TaskRejectedException exception) {
            log.warn(
                    "Interview AI queue rejected work; recovery will retry: "
                            + "sessionId={}, stage={}",
                    sessionId,
                    stage);
        }
    }

    private void runClaimed(
            Long sessionId,
            SessionProcessingStage stage,
            UUID processingToken) {
        Optional<Instant> retryAt = switch (stage) {
            case SCRIPT_GENERATION -> scriptWorker.process(sessionId, processingToken);
            case NEXT_TURN -> nextTurnWorker.process(sessionId, processingToken);
            case SCORING -> Optional.empty();
        };
        retryAt.ifPresent(when -> scheduleRecovery(sessionId, stage, when));
    }

    private void scheduleRecovery(
            Long sessionId,
            SessionProcessingStage stage,
            Instant retryAt) {
        try {
            scheduler.schedule(() -> claimAndDispatch(sessionId, stage), retryAt);
        } catch (TaskRejectedException exception) {
            log.warn(
                    "Interview retry trigger was rejected; periodic recovery will retry: "
                            + "sessionId={}, stage={}",
                    sessionId,
                    stage);
        }
    }
}
