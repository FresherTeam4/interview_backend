package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.session.InterviewAnswerResponse;
import com.baseProject.myBaseProject.dto.session.InterviewConversationResponse;
import com.baseProject.myBaseProject.dto.session.SubmitInterviewAnswerRequest;

public interface InterviewConversationService {
    InterviewConversationResponse start(Long userId, Long sessionId);

    InterviewConversationResponse get(Long userId, Long sessionId);

    InterviewAnswerResponse answer(
            Long userId,
            Long sessionId,
            String idempotencyKey,
            SubmitInterviewAnswerRequest request);

    InterviewConversationResponse finish(Long userId, Long sessionId);

    void continueAfterRealtimeFallback(Long userId, Long sessionId);
}
