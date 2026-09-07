package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.interview.InterviewScoringRecoveryService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class InterviewScoringRecoveryListener {
    private final InterviewScoringRecoveryService recoveryService;
    private final Instant applicationStartedAt;

    public InterviewScoringRecoveryListener(
            InterviewScoringRecoveryService recoveryService, Clock clock) {
        this.recoveryService = recoveryService;
        // Mốc này phân biệt job bị gián đoạn từ tiến trình cũ với job mới của lần chạy hiện tại.
        this.applicationStartedAt = clock.instant();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        recoveryService.failInterruptedScoring(applicationStartedAt);
    }
}
