package com.baseProject.myBaseProject.interview.workflow;

import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionAcceptedResponse;
import com.baseProject.myBaseProject.dto.interview.RetryInterviewSessionRequest;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.SessionFailureStage;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.exception.InterviewAiUnavailableException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.interview.lifecycle.SessionStateMachine;
import com.baseProject.myBaseProject.mapper.InterviewSessionMapper;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewWorkflowRetryService {

    private static final String RETRY_TRANSITION_REASON = "User retried interview AI workflow";

    private final InterviewProperties properties;
    private final SessionStateMachine stateMachine;
    private final SessionProcessingClaimService claimService;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewWorkflowDispatcher workflowDispatcher;
    private final InterviewSessionMapper sessionMapper;

    @Transactional
    public InterviewSessionAcceptedResponse retry(
            Long userId,
            Long sessionId,
            RetryInterviewSessionRequest request) {
        ensureEnabled();
        InterviewSession retrying = stateMachine.retryUserWorkflow(
                userId,
                sessionId,
                request.expectedVersion(),
                Set.of(
                        SessionFailureStage.SCRIPT_GENERATION,
                        SessionFailureStage.NEXT_TURN),
                RETRY_TRANSITION_REASON);
        UUID processingToken = UUID.randomUUID();
        SessionProcessingStage processingStage = retrying.getProcessingStage();
        boolean claimed = claimService.claim(
                retrying.getId(),
                processingStage,
                processingToken,
                properties.processingLease());
        if (!claimed) {
            throw new IllegalStateException(
                    "Retried interview work could not be claimed, sessionId="
                            + retrying.getId());
        }

        InterviewSession claimedSession = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        if (processingStage != SessionProcessingStage.SCRIPT_GENERATION
                && processingStage != SessionProcessingStage.NEXT_TURN) {
            throw new IllegalStateException(
                    "Unsupported retried processing stage " + processingStage);
        }
        workflowDispatcher.dispatchAfterCommit(sessionId, processingStage, processingToken);
        return sessionMapper.toAcceptedResponse(claimedSession);
    }

    private void ensureEnabled() {
        if (!properties.enabled()) {
            throw new InterviewAiUnavailableException();
        }
    }
}
