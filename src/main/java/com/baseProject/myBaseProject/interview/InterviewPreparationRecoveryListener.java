package com.baseProject.myBaseProject.interview;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class InterviewPreparationRecoveryListener {
    private final InterviewPreparationRecoveryService recoveryService;
    private final Instant applicationStartedAt;

    public InterviewPreparationRecoveryListener(
            InterviewPreparationRecoveryService recoveryService, Clock clock) {
        this.recoveryService = recoveryService;
        // Mốc này phân biệt job tồn đọng từ tiến trình cũ với job của lần chạy hiện tại.
        this.applicationStartedAt = clock.instant();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        recoveryService.failInterruptedPreparations(applicationStartedAt);
    }
}
