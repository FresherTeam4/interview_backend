package com.baseProject.myBaseProject.cv;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class CvProcessingRecoveryListener {

    private final CvProcessingRecoveryService recoveryService;
    private final Instant applicationStartedAt;

    public CvProcessingRecoveryListener(CvProcessingRecoveryService recoveryService, Clock clock) {
        this.recoveryService = recoveryService;
        this.applicationStartedAt = clock.instant();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        recoveryService.failInterruptedJobs(applicationStartedAt);
    }
}
