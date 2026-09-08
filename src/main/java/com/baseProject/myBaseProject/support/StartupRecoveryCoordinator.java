package com.baseProject.myBaseProject.support;

import com.baseProject.myBaseProject.cv.CvProcessingRecoveryService;
import com.baseProject.myBaseProject.interview.InterviewPreparationRecoveryService;
import com.baseProject.myBaseProject.interview.InterviewScoringRecoveryService;
import com.baseProject.myBaseProject.jobdescription.JobDescriptionProcessingRecoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
public class StartupRecoveryCoordinator {
    private final CvProcessingRecoveryService cvRecoveryService;
    private final JobDescriptionProcessingRecoveryService jobDescriptionRecoveryService;
    private final InterviewPreparationRecoveryService interviewPreparationRecoveryService;
    private final InterviewScoringRecoveryService interviewScoringRecoveryService;
    private final Instant applicationStartedAt;

    public StartupRecoveryCoordinator(
            CvProcessingRecoveryService cvRecoveryService,
            JobDescriptionProcessingRecoveryService jobDescriptionRecoveryService,
            InterviewPreparationRecoveryService interviewPreparationRecoveryService,
            InterviewScoringRecoveryService interviewScoringRecoveryService,
            Clock clock) {
        this.cvRecoveryService = cvRecoveryService;
        this.jobDescriptionRecoveryService = jobDescriptionRecoveryService;
        this.interviewPreparationRecoveryService = interviewPreparationRecoveryService;
        this.interviewScoringRecoveryService = interviewScoringRecoveryService;
        this.applicationStartedAt = clock.instant();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        recover("CV processing",
                () -> cvRecoveryService.failInterruptedJobs(applicationStartedAt));

        recover("job description processing",
                () -> jobDescriptionRecoveryService.failInterruptedJobs(applicationStartedAt));

        recover("interview preparation",
                () -> interviewPreparationRecoveryService.failInterruptedPreparations(applicationStartedAt));

        recover("interview scoring",
                () -> interviewScoringRecoveryService.failInterruptedScoring(applicationStartedAt));
    }

    private void recover(String operation, Runnable recovery) {
        try {
            recovery.run();
        } catch (Exception exception) {
            log.error("Failed to recover interrupted {} jobs", operation, exception);
        }
    }
}
