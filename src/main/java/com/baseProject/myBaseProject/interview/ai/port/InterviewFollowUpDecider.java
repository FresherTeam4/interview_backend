package com.baseProject.myBaseProject.interview.ai.port;

import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionInput;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionOutcome;

public interface InterviewFollowUpDecider {

    FollowUpDecisionOutcome decide(FollowUpDecisionInput input);
}
