package com.baseProject.myBaseProject.jobdescription;

import java.time.Instant;

public interface JobDescriptionProcessingRecoveryService {
    int failInterruptedJobs(Instant applicationStartedAt);
}
