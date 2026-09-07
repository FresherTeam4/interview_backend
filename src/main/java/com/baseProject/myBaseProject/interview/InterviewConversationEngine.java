package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewReplyResult;
import com.baseProject.myBaseProject.interview.model.InterviewContext;
import com.baseProject.myBaseProject.interview.model.InterviewTurnContext;

import java.util.List;

public interface InterviewConversationEngine {
    InterviewReplyResult reply(
            InterviewContext context,
            List<InterviewTurnContext> recentTurns,
            long remainingSeconds);
}
