package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.interview.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionAcceptedResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionResponse;
import com.baseProject.myBaseProject.dto.interview.RetryInterviewSessionRequest;

public interface InterviewSessionService {

    InterviewSessionAcceptedResponse create(
            Long userId,
            String idempotencyKey,
            CreateInterviewSessionRequest request);

    InterviewSessionResponse get(Long userId, Long sessionId);

    InterviewSessionAcceptedResponse retry(
            Long userId,
            Long sessionId,
            RetryInterviewSessionRequest request);
}
