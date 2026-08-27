package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.config.AsyncConfig;
import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Slf4j
@Component
public class InterviewNextTurnWorkflowDispatcher {

    private final InterviewProperties properties;
    private final SessionProcessingClaimService claimService;
    private final InterviewNextTurnWorker worker;
    private final ThreadPoolTaskExecutor executor;

    public InterviewNextTurnWorkflowDispatcher(
            InterviewProperties properties,
            SessionProcessingClaimService claimService,
            InterviewNextTurnWorker worker,
            @Qualifier(AsyncConfig.INTERVIEW_AI_EXECUTOR) ThreadPoolTaskExecutor executor) {
        this.properties = properties;
        this.claimService = claimService;
        this.worker = worker;
        this.executor = executor;
    }

    public void dispatchAfterCommit(Long sessionId, UUID processingToken) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatchClaimed(sessionId, processingToken);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchClaimed(sessionId, processingToken);
            }
        });
    }

    public boolean claimAndDispatch(Long sessionId) {
        if (!properties.enabled()) {
            return false;
        }
        UUID processingToken = UUID.randomUUID();
        boolean claimed = claimService.claim(
                sessionId,
                SessionProcessingStage.NEXT_TURN,
                processingToken,
                properties.processingLease());
        if (claimed) {
            dispatchClaimed(sessionId, processingToken);
        }
        return claimed;
    }

    private void dispatchClaimed(Long sessionId, UUID processingToken) {
        if (!properties.enabled()) {
            return;
        }
        try {
            executor.execute(() -> worker.process(sessionId, processingToken));
        } catch (TaskRejectedException exception) {
            log.warn(
                    "Interview AI queue rejected next-turn work; recovery will retry: sessionId={}",
                    sessionId);
        }
    }
}
