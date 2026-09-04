package com.baseProject.myBaseProject.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowDispatcher;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class InterviewWorkflowRecoveryJobTest {

    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");
    private static final Instant STALE_BEFORE = Instant.parse("2026-08-26T07:58:30Z");

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private SessionTurnRepository turnRepository;
    @Mock
    private InterviewWorkflowDispatcher workflowDispatcher;

    @Test
    void periodicRecoveryScansAndAtomicallyClaimsEveryWorkflowStage() {
        InterviewWorkflowRecoveryJob job = job(true);
        when(sessionRepository.findRecoverableWorkIds(
                        eq(List.of(SessionStatus.SCRIPT_GENERATING)),
                        eq(List.of(SessionProcessingStage.SCRIPT_GENERATION)),
                        eq(NOW),
                        eq(STALE_BEFORE),
                        any(Pageable.class)))
                .thenReturn(List.of(10L));
        when(turnRepository.findRecoverableNextTurnSessionIds(
                        eq(SessionStatus.IN_PROGRESS),
                        eq(List.of(AwaitingAction.ENGINE_RESPONSE, AwaitingAction.ENGINE_RETRY)),
                        eq(SessionProcessingStage.NEXT_TURN),
                        eq(NOW),
                        eq(STALE_BEFORE),
                        any(Pageable.class)))
                .thenReturn(List.of(11L));
        when(sessionRepository.findRecoverableWorkIds(
                        eq(List.of(SessionStatus.SCORING)),
                        eq(List.of(SessionProcessingStage.SCORING)),
                        eq(NOW),
                        eq(STALE_BEFORE),
                        any(Pageable.class)))
                .thenReturn(List.of(12L));
        when(workflowDispatcher.claimAndDispatch(10L, SessionProcessingStage.SCRIPT_GENERATION))
                .thenReturn(true);
        when(workflowDispatcher.claimAndDispatch(11L, SessionProcessingStage.NEXT_TURN))
                .thenReturn(true);
        when(workflowDispatcher.claimAndDispatch(12L, SessionProcessingStage.SCORING))
                .thenReturn(true);

        job.recoverPeriodically();

        verify(workflowDispatcher).claimAndDispatch(
                10L,
                SessionProcessingStage.SCRIPT_GENERATION);
        verify(workflowDispatcher).claimAndDispatch(11L, SessionProcessingStage.NEXT_TURN);
        verify(workflowDispatcher).claimAndDispatch(12L, SessionProcessingStage.SCORING);
    }

    @Test
    void activeLeaseIsNotDispatchedWhenAtomicClaimLosesRace() {
        InterviewWorkflowRecoveryJob job = job(true);
        when(sessionRepository.findRecoverableWorkIds(
                        eq(List.of(SessionStatus.SCRIPT_GENERATING)),
                        eq(List.of(SessionProcessingStage.SCRIPT_GENERATION)),
                        eq(NOW),
                        eq(STALE_BEFORE),
                        any(Pageable.class)))
                .thenReturn(List.of(10L));
        when(workflowDispatcher.claimAndDispatch(10L, SessionProcessingStage.SCRIPT_GENERATION))
                .thenReturn(false);

        job.recoverPeriodically();

        verify(workflowDispatcher).claimAndDispatch(
                10L,
                SessionProcessingStage.SCRIPT_GENERATION);
        verify(workflowDispatcher, never()).claimAndDispatch(
                10L,
                SessionProcessingStage.NEXT_TURN);
    }

    @Test
    void disabledInterviewEngineDoesNotRecover() {
        job(false).recoverPeriodically();

        verifyNoInteractions(sessionRepository, turnRepository, workflowDispatcher);
    }

    private InterviewWorkflowRecoveryJob job(boolean enabled) {
        return new InterviewWorkflowRecoveryJob(
                new InterviewProperties(enabled, 24, 90, 5, 10_000),
                sessionRepository,
                turnRepository,
                workflowDispatcher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
