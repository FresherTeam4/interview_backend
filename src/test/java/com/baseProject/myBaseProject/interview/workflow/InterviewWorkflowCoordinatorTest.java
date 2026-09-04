package com.baseProject.myBaseProject.interview.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.SessionFailureStage;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.exception.InterviewScoringException;
import com.baseProject.myBaseProject.interview.lifecycle.SessionStateMachine;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowCoordinator.FailureOutcome;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class InterviewWorkflowCoordinatorTest {

    private static final Long SESSION_ID = 42L;
    private static final UUID TOKEN =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private SessionProcessingClaimService claimService;
    @Mock
    private SessionStateMachine stateMachine;
    @Mock
    private InterviewSession session;

    private InterviewWorkflowCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new InterviewWorkflowCoordinator(
                sessionRepository,
                claimService,
                stateMachine,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void scoringRetriesUseTwoThenTenSecondBackoff() {
        ownedScoringClaim((short) 1);
        when(claimService.releaseForRetry(
                        SESSION_ID,
                        SessionProcessingStage.SCORING,
                        TOKEN,
                        NOW.plusSeconds(2),
                        InterviewScoringException.timeout(null).getStatusMessage()))
                .thenReturn(true);

        FailureOutcome first = coordinator.handleScoringFailure(
                SESSION_ID,
                TOKEN,
                InterviewScoringException.timeout(null));

        assertThat(first.retryAt()).isEqualTo(NOW.plusSeconds(2));

        when(session.getProcessingAttempts()).thenReturn((short) 2);
        when(claimService.releaseForRetry(
                        SESSION_ID,
                        SessionProcessingStage.SCORING,
                        TOKEN,
                        NOW.plusSeconds(10),
                        InterviewScoringException.timeout(null).getStatusMessage()))
                .thenReturn(true);

        FailureOutcome second = coordinator.handleScoringFailure(
                SESSION_ID,
                TOKEN,
                InterviewScoringException.timeout(null));

        assertThat(second.retryAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void staleWorkerCannotReleaseClaimOwnedByNewWorker() {
        when(sessionRepository.findByIdForUpdate(SESSION_ID))
                .thenReturn(Optional.of(session));
        when(session.getProcessingStage()).thenReturn(SessionProcessingStage.SCORING);
        when(session.getProcessingToken()).thenReturn(UUID.randomUUID().toString());

        FailureOutcome outcome = coordinator.handleScoringFailure(
                SESSION_ID,
                TOKEN,
                InterviewScoringException.timeout(null));

        assertThat(outcome.ignored()).isTrue();
        verify(claimService, never()).releaseForRetry(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(stateMachine, never()).failWorkflow(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void scoringStopsAfterThirdAttempt() {
        ownedScoringClaim((short) 3);
        when(session.getVersion()).thenReturn(9L);

        FailureOutcome outcome = coordinator.handleScoringFailure(
                SESSION_ID,
                TOKEN,
                InterviewScoringException.timeout(null));

        assertThat(outcome.failed()).isTrue();
        assertThat(outcome.retryAt()).isNull();
        verify(claimService, never()).releaseForRetry(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(stateMachine).failWorkflow(
                SESSION_ID,
                9L,
                TOKEN,
                SessionFailureStage.SCORING,
                InterviewScoringException.timeout(null).getStatusMessage(),
                "Scoring failed: TIMEOUT");
    }

    private void ownedScoringClaim(short attempts) {
        when(sessionRepository.findByIdForUpdate(SESSION_ID))
                .thenReturn(Optional.of(session));
        when(session.getProcessingStage()).thenReturn(SessionProcessingStage.SCORING);
        when(session.getProcessingToken()).thenReturn(TOKEN.toString());
        when(session.getStatus()).thenReturn(SessionStatus.SCORING);
        when(session.getProcessingAttempts()).thenReturn(attempts);
    }
}
