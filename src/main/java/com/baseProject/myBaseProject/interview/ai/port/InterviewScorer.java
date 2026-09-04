package com.baseProject.myBaseProject.interview.ai.port;

import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringInput;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringOutcome;

public interface InterviewScorer {

    ScoringOutcome score(ScoringInput input);
}
