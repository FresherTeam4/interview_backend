package com.baseProject.myBaseProject.interview.voice;

import com.baseProject.myBaseProject.config.AsyncConfig;

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
public class VoiceTranscriptionDispatcher {

    private final VoiceTranscriptionClaimService claimService;
    private final VoiceTranscriptionWorker worker;
    private final ThreadPoolTaskExecutor executor;
    private final TaskScheduler scheduler;

    public VoiceTranscriptionDispatcher(
            VoiceTranscriptionClaimService claimService,
            VoiceTranscriptionWorker worker,
            @Qualifier(AsyncConfig.INTERVIEW_VOICE_EXECUTOR) ThreadPoolTaskExecutor executor,
            @Qualifier(AsyncConfig.INTERVIEW_WORKFLOW_SCHEDULER) TaskScheduler scheduler) {
        this.claimService = claimService;
        this.worker = worker;
        this.executor = executor;
        this.scheduler = scheduler;
    }

    public void dispatchAfterCommit(Long attemptId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            claimAndDispatch(attemptId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                claimAndDispatch(attemptId);
            }
        });
    }

    public boolean claimAndDispatch(Long attemptId) {
        UUID token = UUID.randomUUID();
        if (!claimService.claim(attemptId, token)) {
            return false;
        }
        dispatchClaimed(attemptId, token);
        return true;
    }

    private void dispatchClaimed(Long attemptId, UUID token) {
        try {
            executor.execute(() -> runClaimed(attemptId, token));
        } catch (TaskRejectedException exception) {
            log.warn(
                    "Voice transcription queue rejected work; recovery will retry: attemptId={}",
                    attemptId);
        }
    }

    private void runClaimed(Long attemptId, UUID token) {
        Optional<Instant> retryAt = worker.process(attemptId, token);
        retryAt.ifPresent(when -> scheduleRecovery(attemptId, when));
    }

    private void scheduleRecovery(Long attemptId, Instant retryAt) {
        try {
            scheduler.schedule(() -> claimAndDispatch(attemptId), retryAt);
        } catch (TaskRejectedException exception) {
            log.warn(
                    "Voice transcription retry trigger was rejected; periodic recovery will retry: attemptId={}",
                    attemptId);
        }
    }
}
