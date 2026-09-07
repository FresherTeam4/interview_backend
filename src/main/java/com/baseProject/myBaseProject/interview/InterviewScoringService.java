package com.baseProject.myBaseProject.interview;

public interface InterviewScoringService {
    void scoreAsync(Long sessionId);

    void markDispatchFailed(Long sessionId);
}
