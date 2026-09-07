package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewAssessmentResult;
import com.baseProject.myBaseProject.interview.model.InterviewScoringContext;

public interface InterviewScoringEngine {
    InterviewAssessmentResult assess(InterviewScoringContext context);
}
