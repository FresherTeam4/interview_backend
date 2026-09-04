package com.baseProject.myBaseProject.cv;

import java.time.Instant;

public interface CvProcessingRecoveryService {

    int failInterruptedJobs(Instant applicationStartedAt);
}
