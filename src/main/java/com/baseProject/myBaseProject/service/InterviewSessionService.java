package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.interview.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionAcceptedResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewRubricResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionSummaryResponse;
import com.baseProject.myBaseProject.dto.interview.RetryInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.interview.SessionVersionRequest;
import com.baseProject.myBaseProject.enums.SessionListScope;

public interface InterviewSessionService {

    InterviewSessionAcceptedResponse create(
            Long userId,
            String idempotencyKey,
            CreateInterviewSessionRequest request);

    InterviewSessionResponse get(Long userId, Long sessionId);

    PageResponse<InterviewSessionSummaryResponse> list(
            Long userId,
            SessionListScope scope,
            int page,
            int size);

    InterviewRubricResponse getRubric(Long userId, Long sessionId);

    InterviewSessionResponse start(
            Long userId,
            Long sessionId,
            SessionVersionRequest request);

    InterviewSessionResponse pause(
            Long userId,
            Long sessionId,
            SessionVersionRequest request);

    InterviewSessionResponse resume(
            Long userId,
            Long sessionId,
            SessionVersionRequest request);

    InterviewSessionAcceptedResponse retry(
            Long userId,
            Long sessionId,
            RetryInterviewSessionRequest request);
}
