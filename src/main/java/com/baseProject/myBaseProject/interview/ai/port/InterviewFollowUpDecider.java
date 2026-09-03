package com.baseProject.myBaseProject.interview.ai.port;

import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionContract.FollowUpDecisionInput;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionContract.FollowUpDecisionOutcome;

public interface InterviewFollowUpDecider {

    FollowUpDecisionOutcome decide(FollowUpDecisionInput input);
}
