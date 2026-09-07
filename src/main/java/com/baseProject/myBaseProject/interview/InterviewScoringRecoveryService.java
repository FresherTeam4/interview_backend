package com.baseProject.myBaseProject.interview;

import java.time.Instant;

public interface InterviewScoringRecoveryService {
    int failInterruptedScoring(Instant applicationStartedAt);
}
