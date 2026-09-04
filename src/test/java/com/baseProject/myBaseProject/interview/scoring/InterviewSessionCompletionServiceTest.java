package com.baseProject.myBaseProject.interview.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionAcceptedResponse;
import com.baseProject.myBaseProject.dto.interview.SessionVersionRequest;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.interview.lifecycle.SessionStateMachine;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowDispatcher;
import com.baseProject.myBaseProject.interview.workflow.SessionProcessingClaimService;
import com.baseProject.myBaseProject.mapper.InterviewSessionMapper;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class InterviewSessionCompletionServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 42L;
    private static final SessionVersionRequest REQUEST = new SessionVersionRequest(9L);

    @Mock
    private SessionStateMachine stateMachine;
    @Mock
    private InterviewScoringStore scoringStore;
    @Mock
    private SessionProcessingClaimService claimService;
    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private InterviewWorkflowDispatcher workflowDispatcher;
    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewSession scoring;
    @Mock
    private InterviewSession completed;
    @Mock
    private InterviewSession claimed;

    private InterviewSessionCompletionService service;

    @BeforeEach
    void setUp() {
        service = new InterviewSessionCompletionService(
                new InterviewProperties(true, 90, 5, 10_000),
                stateMachine,
                scoringStore,
                claimService,
                sessionRepository,
                workflowDispatcher,
                sessionMapper);
        when(stateMachine.completeEarly(
                        USER_ID,
                        SESSION_ID,
                        REQUEST.expectedVersion(),
                        "User completed interview early"))
                .thenReturn(scoring);
    }

    @Test
    void completeWithoutAnswersCommitsInsufficientEvidenceSynchronously() {
        InterviewSessionAcceptedResponse response = accepted(SessionStatus.COMPLETED);
        when(scoring.getAnsweredQuestionCount()).thenReturn((short) 0);
        when(scoringStore.commitInsufficientEvidence(scoring)).thenReturn(completed);
        when(sessionMapper.toAcceptedResponse(completed)).thenReturn(response);

        assertThat(service.complete(USER_ID, SESSION_ID, REQUEST)).isSameAs(response);

        verify(claimService, never()).claim(any(), any(), any(), any());
        verify(workflowDispatcher, never()).dispatchAfterCommit(any(), any(), any());
    }

    @Test
    void completeWithAnswersClaimsAndDispatchesScoring() {
        InterviewSessionAcceptedResponse response = accepted(SessionStatus.SCORING);
        when(scoring.getAnsweredQuestionCount()).thenReturn((short) 2);
        when(claimService.claim(
                        org.mockito.ArgumentMatchers.eq(SESSION_ID),
                        org.mockito.ArgumentMatchers.eq(SessionProcessingStage.SCORING),
                        any(UUID.class),
                        org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(90))))
                .thenReturn(true);
        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(claimed));
        when(sessionMapper.toAcceptedResponse(claimed)).thenReturn(response);

        assertThat(service.complete(USER_ID, SESSION_ID, REQUEST)).isSameAs(response);

        verify(workflowDispatcher).dispatchAfterCommit(
                org.mockito.ArgumentMatchers.eq(SESSION_ID),
                org.mockito.ArgumentMatchers.eq(SessionProcessingStage.SCORING),
                any(UUID.class));
    }

    private InterviewSessionAcceptedResponse accepted(SessionStatus status) {
        return new InterviewSessionAcceptedResponse(
                SESSION_ID,
                status,
                status == SessionStatus.SCORING ? AwaitingAction.REPORT : AwaitingAction.NONE,
                10L,
                Instant.parse("2026-08-26T08:00:00Z"));
    }
}
