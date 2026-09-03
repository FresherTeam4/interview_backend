package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.interview.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.interview.InterviewRubricResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionAcceptedResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionSummaryResponse;
import com.baseProject.myBaseProject.dto.interview.RetryInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.interview.SessionVersionRequest;
import com.baseProject.myBaseProject.dto.interview.SubmitTextAnswerRequest;
import com.baseProject.myBaseProject.dto.interview.TextAnswerAcceptedResponse;
import com.baseProject.myBaseProject.enums.SessionListScope;
import com.baseProject.myBaseProject.interview.lifecycle.InterviewSessionCreationService;
import com.baseProject.myBaseProject.interview.lifecycle.InterviewSessionLifecycleService;
import com.baseProject.myBaseProject.interview.lifecycle.InterviewSessionQueryService;
import com.baseProject.myBaseProject.interview.turn.InterviewAnswerService;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowRetryService;
import com.baseProject.myBaseProject.service.InterviewSessionService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterviewSessionServiceImpl implements InterviewSessionService {

    private final InterviewSessionCreationService creationService;
    private final InterviewSessionQueryService queryService;
    private final InterviewSessionLifecycleService lifecycleService;
    private final InterviewAnswerService answerService;
    private final InterviewWorkflowRetryService retryService;

    @Override
    public InterviewSessionAcceptedResponse create(
            Long userId,
            String idempotencyKey,
            CreateInterviewSessionRequest request) {
        return creationService.create(userId, idempotencyKey, request);
    }

    @Override
    public InterviewSessionResponse get(Long userId, Long sessionId) {
        return queryService.get(userId, sessionId);
    }

    @Override
    public PageResponse<InterviewSessionSummaryResponse> list(
            Long userId,
            SessionListScope scope,
            int page,
            int size) {
        return queryService.list(userId, scope, page, size);
    }

    @Override
    public InterviewRubricResponse getRubric(Long userId, Long sessionId) {
        return queryService.getRubric(userId, sessionId);
    }

    @Override
    public InterviewSessionResponse start(
            Long userId,
            Long sessionId,
            SessionVersionRequest request) {
        return lifecycleService.start(userId, sessionId, request);
    }

    @Override
    public InterviewSessionResponse pause(
            Long userId,
            Long sessionId,
            SessionVersionRequest request) {
        return lifecycleService.pause(userId, sessionId, request);
    }

    @Override
    public InterviewSessionResponse resume(
            Long userId,
            Long sessionId,
            SessionVersionRequest request) {
        return lifecycleService.resume(userId, sessionId, request);
    }

    @Override
    public TextAnswerAcceptedResponse submitTextAnswer(
            Long userId,
            Long sessionId,
            SubmitTextAnswerRequest request) {
        return answerService.submitTextAnswer(userId, sessionId, request);
    }

    @Override
    public InterviewSessionAcceptedResponse retry(
            Long userId,
            Long sessionId,
            RetryInterviewSessionRequest request) {
        return retryService.retry(userId, sessionId, request);
    }
}
