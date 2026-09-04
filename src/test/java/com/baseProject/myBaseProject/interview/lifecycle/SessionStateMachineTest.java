package com.baseProject.myBaseProject.interview.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionStateTransition;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionEndReason;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.SessionTransitionActor;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.interview.snapshot.SessionContextSnapshotFactory;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionContextSnapshotRepository;
import com.baseProject.myBaseProject.repository.SessionStateTransitionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class SessionStateMachineTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 42L;
    private static final long EXPECTED_VERSION = 9L;
    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private SessionContextSnapshotRepository snapshotRepository;
    @Mock
    private SessionStateTransitionRepository transitionRepository;
    @Mock
    private SessionContextSnapshotFactory snapshotFactory;
    @Mock
    private InterviewSession session;

    private SessionStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new SessionStateMachine(
                sessionRepository,
                snapshotRepository,
                transitionRepository,
                snapshotFactory,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void completeEarlyMovesStableInterviewToScoring() {
        ownedSession(AwaitingAction.CANDIDATE_ANSWER);
        when(sessionRepository.saveAndFlush(session)).thenReturn(session);

        InterviewSession result = stateMachine.completeEarly(
                USER_ID,
                SESSION_ID,
                EXPECTED_VERSION,
                "User completed interview early");

        assertThat(result).isSameAs(session);
        verify(session).applyStateTransition(
                SessionStatus.SCORING,
                AwaitingAction.REPORT,
                SessionEndReason.USER_COMPLETED_EARLY,
                null,
                null,
                SessionProcessingStage.SCORING,
                true,
                true,
                NOW);

        ArgumentCaptor<SessionStateTransition> transitionCaptor =
                ArgumentCaptor.forClass(SessionStateTransition.class);
        verify(transitionRepository).save(transitionCaptor.capture());
        SessionStateTransition transition = transitionCaptor.getValue();
        assertThat(transition.getSession()).isSameAs(session);
        assertThat(transition.getFromStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(transition.getToStatus()).isEqualTo(SessionStatus.SCORING);
        assertThat(transition.getActor()).isEqualTo(SessionTransitionActor.USER);
        assertThat(transition.getReason()).isEqualTo("User completed interview early");
        assertThat(transition.getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    void completeEarlyRejectsSessionWhileEngineIsResponding() {
        ownedSession(AwaitingAction.ENGINE_RESPONSE);

        assertThatThrownBy(() -> stateMachine.completeEarly(
                        USER_ID,
                        SESSION_ID,
                        EXPECTED_VERSION,
                        "User completed interview early"))
                .isInstanceOf(SessionInvalidStateException.class);
    }

    @Test
    void completeScoringClearsWorkflowAndPreservesEndReason() {
        UUID token = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(session.getId()).thenReturn(SESSION_ID);
        when(session.getStatus()).thenReturn(SessionStatus.SCORING);
        when(session.getAwaitingAction()).thenReturn(AwaitingAction.REPORT);
        when(session.getProcessingStage()).thenReturn(SessionProcessingStage.SCORING);
        when(session.getProcessingToken()).thenReturn(token.toString());
        when(session.getEndReason()).thenReturn(SessionEndReason.USER_COMPLETED);
        when(sessionRepository.saveAndFlush(session)).thenReturn(session);

        stateMachine.completeScoring(session, token, "Interview report committed");

        verify(session).applyStateTransition(
                SessionStatus.COMPLETED,
                AwaitingAction.NONE,
                SessionEndReason.USER_COMPLETED,
                null,
                null,
                null,
                false,
                false,
                NOW);
    }

    @Test
    void abandonReadySessionCreatesTerminalUserTransition() {
        when(sessionRepository.findOwnedByIdForUpdate(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(session));
        when(session.getVersion()).thenReturn(EXPECTED_VERSION);
        when(session.getStatus()).thenReturn(SessionStatus.READY);
        when(sessionRepository.saveAndFlush(session)).thenReturn(session);

        stateMachine.abandon(
                USER_ID,
                SESSION_ID,
                EXPECTED_VERSION,
                "User abandoned interview");

        verify(session).applyStateTransition(
                SessionStatus.ABANDONED,
                AwaitingAction.NONE,
                SessionEndReason.USER_ABANDONED,
                null,
                null,
                null,
                false,
                true,
                NOW);
    }

    @Test
    void abandonIsIdempotentAfterSessionAlreadyAbandoned() {
        when(sessionRepository.findOwnedByIdForUpdate(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(session));
        when(session.getStatus()).thenReturn(SessionStatus.ABANDONED);

        assertThat(stateMachine.abandon(
                USER_ID,
                SESSION_ID,
                -1,
                "User abandoned interview")).isSameAs(session);

        verifyNoInteractions(transitionRepository);
    }

    private void ownedSession(AwaitingAction awaitingAction) {
        when(sessionRepository.findOwnedByIdForUpdate(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(session));
        when(session.getVersion()).thenReturn(EXPECTED_VERSION);
        when(session.getStatus()).thenReturn(SessionStatus.IN_PROGRESS);
        when(session.getAwaitingAction()).thenReturn(awaitingAction);
    }
}
