package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.session.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.session.InterviewSessionOptionsResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionResponse;

public interface InterviewSessionService {
    InterviewSessionOptionsResponse options();

    InterviewSessionResponse create(
            Long userId, String idempotencyKey, CreateInterviewSessionRequest request);

    InterviewSessionResponse get(Long userId, Long sessionId);

    InterviewSessionResponse retryPreparation(Long userId, Long sessionId);
}
