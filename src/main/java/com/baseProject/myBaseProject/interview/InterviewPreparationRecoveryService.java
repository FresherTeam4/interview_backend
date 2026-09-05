package com.baseProject.myBaseProject.interview;

import java.time.Instant;

public interface InterviewPreparationRecoveryService {
    int failInterruptedPreparations(Instant applicationStartedAt);
}
