package com.baseProject.myBaseProject.interview.scoring;

import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionAcceptedResponse;
import com.baseProject.myBaseProject.dto.interview.SessionVersionRequest;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.exception.InterviewAiUnavailableException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.interview.lifecycle.SessionStateMachine;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowDispatcher;
import com.baseProject.myBaseProject.interview.workflow.SessionProcessingClaimService;
import com.baseProject.myBaseProject.mapper.InterviewSessionMapper;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewSessionCompletionService {

    private static final String COMPLETE_EARLY_REASON = "User completed interview early";
    private static final String ABANDON_REASON = "User abandoned interview";

    private final InterviewProperties properties;
    private final SessionStateMachine stateMachine;
    private final InterviewScoringStore scoringStore;
    private final SessionProcessingClaimService claimService;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewWorkflowDispatcher workflowDispatcher;
    private final InterviewSessionMapper sessionMapper;

    @Transactional
    public InterviewSessionAcceptedResponse complete(
            Long userId,
            Long sessionId,
            SessionVersionRequest request) {
        InterviewSession scoring = stateMachine.completeEarly(
                userId,
                sessionId,
                request.expectedVersion(),
                COMPLETE_EARLY_REASON);
        if (scoring.getAnsweredQuestionCount() == 0) {
            return sessionMapper.toAcceptedResponse(
                    scoringStore.commitInsufficientEvidence(scoring));
        }
        if (!properties.enabled()) {
            throw new InterviewAiUnavailableException();
        }

        UUID processingToken = UUID.randomUUID();
        boolean claimed = claimService.claim(
                sessionId,
                SessionProcessingStage.SCORING,
                processingToken,
                properties.processingLease());
        if (!claimed) {
            throw new IllegalStateException(
                    "Scoring work could not be claimed, sessionId=" + sessionId);
        }
        InterviewSession claimedSession = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        workflowDispatcher.dispatchAfterCommit(
                sessionId,
                SessionProcessingStage.SCORING,
                processingToken);
        return sessionMapper.toAcceptedResponse(claimedSession);
    }

    @Transactional
    public InterviewSessionAcceptedResponse abandon(
            Long userId,
            Long sessionId,
            SessionVersionRequest request) {
        InterviewSession abandoned = stateMachine.abandon(
                userId,
                sessionId,
                request.expectedVersion(),
                ABANDON_REASON);
        return sessionMapper.toAcceptedResponse(abandoned);
    }
}
