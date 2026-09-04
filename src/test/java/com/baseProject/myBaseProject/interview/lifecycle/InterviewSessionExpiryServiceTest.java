package com.baseProject.myBaseProject.interview.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.interview.lifecycle.InterviewSessionExpiryService.ExpiryOutcome;
import com.baseProject.myBaseProject.interview.scoring.InterviewScoringStore;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowDispatcher;
import com.baseProject.myBaseProject.interview.workflow.SessionProcessingClaimService;
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
class InterviewSessionExpiryServiceTest {

    private static final Long SESSION_ID = 42L;
    private static final Instant CUTOFF = Instant.parse("2026-08-25T08:00:00Z");

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private SessionStateMachine stateMachine;
    @Mock
    private InterviewScoringStore scoringStore;
    @Mock
    private SessionProcessingClaimService claimService;
    @Mock
    private InterviewWorkflowDispatcher workflowDispatcher;
    @Mock
    private InterviewSession session;
    @Mock
    private InterviewSession scoring;
    @Mock
    private InterviewSession completed;

    private InterviewSessionExpiryService service;

    @BeforeEach
    void setUp() {
        service = new InterviewSessionExpiryService(
                new InterviewProperties(true, 24, 90, 5, 10_000),
                sessionRepository,
                stateMachine,
                scoringStore,
                claimService,
                workflowDispatcher);
    }

    @Test
    void inactiveSessionWithoutAnswersCompletesWithInsufficientEvidence() {
        staleSession(SessionStatus.READY);
        when(stateMachine.timeout(session, CUTOFF, "Interview expired after inactivity"))
                .thenReturn(scoring);
        when(scoring.getAnsweredQuestionCount()).thenReturn((short) 0);
        when(scoringStore.commitInsufficientEvidence(scoring)).thenReturn(completed);

        assertThat(service.expireIfInactive(SESSION_ID, CUTOFF))
                .isEqualTo(ExpiryOutcome.COMPLETED_WITHOUT_EVIDENCE);

        verify(scoringStore).commitInsufficientEvidence(scoring);
        verifyNoInteractions(claimService, workflowDispatcher);
    }

    @Test
    void inactiveSessionWithAnswersClaimsAndDispatchesPartialScoring() {
        staleSession(SessionStatus.IN_PROGRESS);
        when(stateMachine.timeout(session, CUTOFF, "Interview expired after inactivity"))
                .thenReturn(scoring);
        when(scoring.getAnsweredQuestionCount()).thenReturn((short) 2);
        when(claimService.claim(
                        eq(SESSION_ID),
                        eq(SessionProcessingStage.SCORING),
                        any(UUID.class),
                        eq(Duration.ofSeconds(90))))
                .thenReturn(true);

        assertThat(service.expireIfInactive(SESSION_ID, CUTOFF))
                .isEqualTo(ExpiryOutcome.SCORING_DISPATCHED);

        verify(workflowDispatcher).dispatchAfterCommit(
                eq(SESSION_ID),
                eq(SessionProcessingStage.SCORING),
                any(UUID.class));
        verify(scoringStore, never()).commitInsufficientEvidence(any());
    }

    @Test
    void freshlyPersistedAnswerWinsRaceAgainstExpiryScan() {
        when(sessionRepository.findByIdForUpdate(SESSION_ID))
                .thenReturn(Optional.of(session));
        when(session.getStatus()).thenReturn(SessionStatus.IN_PROGRESS);
        when(session.getLastActivityAt()).thenReturn(CUTOFF.plusSeconds(1));

        assertThat(service.expireIfInactive(SESSION_ID, CUTOFF))
                .isEqualTo(ExpiryOutcome.SKIPPED);

        verifyNoInteractions(stateMachine, scoringStore, claimService, workflowDispatcher);
    }

    @Test
    void sessionCompletedAfterScanIsSkippedAfterLock() {
        when(sessionRepository.findByIdForUpdate(SESSION_ID))
                .thenReturn(Optional.of(session));
        when(session.getStatus()).thenReturn(SessionStatus.COMPLETED);

        assertThat(service.expireIfInactive(SESSION_ID, CUTOFF))
                .isEqualTo(ExpiryOutcome.SKIPPED);

        verifyNoInteractions(stateMachine, scoringStore, claimService, workflowDispatcher);
    }

    private void staleSession(SessionStatus status) {
        when(sessionRepository.findByIdForUpdate(SESSION_ID))
                .thenReturn(Optional.of(session));
        when(session.getStatus()).thenReturn(status);
        when(session.getLastActivityAt()).thenReturn(CUTOFF);
    }
}
