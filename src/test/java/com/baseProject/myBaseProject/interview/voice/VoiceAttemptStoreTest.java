package com.baseProject.myBaseProject.interview.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;
import com.baseProject.myBaseProject.enums.AudioFormat;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionMode;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.enums.VoiceAttemptStatus;
import com.baseProject.myBaseProject.exception.CurrentPromptMismatchException;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.interview.voice.VoiceAttemptStore.PersistResult;
import com.baseProject.myBaseProject.interview.voice.VoiceAttemptStore.VoiceAttemptDraft;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowDispatcher;

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

@ExtendWith(MockitoExtension.class)
class VoiceAttemptStoreTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 42L;
    private static final Long PROMPT_ID = 205L;
    private static final Long QUESTION_ID = 103L;
    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private SessionTurnRepository turnRepository;
    @Mock
    private VoiceAnswerAttemptRepository attemptRepository;
    @Mock
    private InterviewWorkflowDispatcher workflowDispatcher;
    @Mock
    private InterviewSession session;
    @Mock
    private SessionTurn prompt;
    @Mock
    private SessionQuestion question;
    @Mock
    private VoiceAnswerAttempt existing;

    private VoiceAttemptStore store;

    @BeforeEach
    void setUp() {
        store = new VoiceAttemptStore(
                sessionRepository,
                turnRepository,
                attemptRepository,
                workflowDispatcher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void persistCreatesNextRecordedAttemptAndUpdatesActivity() {
        validLockedSession();
        when(attemptRepository.findMaxAttemptNo(SESSION_ID, QUESTION_ID))
                .thenReturn((short) 1);
        when(attemptRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PersistResult result = store.persist(USER_ID, SESSION_ID, draft());

        assertThat(result.created()).isTrue();
        ArgumentCaptor<VoiceAnswerAttempt> attempt =
                ArgumentCaptor.forClass(VoiceAnswerAttempt.class);
        verify(attemptRepository).save(attempt.capture());
        assertThat(attempt.getValue().getAttemptNo()).isEqualTo((short) 2);
        assertThat(attempt.getValue().getStatus()).isEqualTo(VoiceAttemptStatus.RECORDED);
        assertThat(attempt.getValue().getQuestion()).isSameAs(question);
        assertThat(attempt.getValue().getPromptTurn()).isSameAs(prompt);
        verify(session).recordVoiceAttempt(NOW);
        verify(sessionRepository).saveAndFlush(session);
    }

    @Test
    void exactReplayReturnsExistingBeforeCheckingStaleVersionOrState() {
        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(session));
        when(attemptRepository.findBySessionIdAndClientAttemptId(SESSION_ID, "attempt-1"))
                .thenReturn(Optional.of(existing));
        sameExistingRequest();

        assertThat(store.findReplayOrValidate(USER_ID, SESSION_ID, draft()))
                .containsSame(existing);

        verify(turnRepository, never())
                .findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(SESSION_ID, USER_ID);
    }

    @Test
    void newAttemptRejectsTextModeAndStalePrompt() {
        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(session));
        when(session.getVersion()).thenReturn(9L);
        when(session.getMode()).thenReturn(SessionMode.TEXT);

        assertThatThrownBy(() -> store.findReplayOrValidate(USER_ID, SESSION_ID, draft()))
                .isInstanceOf(SessionInvalidStateException.class);

        when(session.getMode()).thenReturn(SessionMode.VOICE_TURN_BASED);
        when(session.getStatus()).thenReturn(SessionStatus.IN_PROGRESS);
        when(session.getAwaitingAction()).thenReturn(AwaitingAction.CANDIDATE_ANSWER);
        when(turnRepository.findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(
                        SESSION_ID, USER_ID))
                .thenReturn(Optional.of(prompt));
        when(prompt.getId()).thenReturn(999L);
        when(prompt.getRole()).thenReturn(TurnRole.INTERVIEWER);
        when(prompt.getQuestion()).thenReturn(question);
        when(prompt.getTurnIndex()).thenReturn(5);
        when(question.getOrdinal()).thenReturn((short) 3);
        when(session.getCurrentQuestionOrdinal()).thenReturn((short) 3);
        when(session.getCurrentFollowupDepth()).thenReturn((short) 0);
        when(session.getNextTurnIndex()).thenReturn(6);

        assertThatThrownBy(() -> store.findReplayOrValidate(USER_ID, SESSION_ID, draft()))
                .isInstanceOf(CurrentPromptMismatchException.class);
    }

    private void validLockedSession() {
        when(sessionRepository.findOwnedByIdForUpdate(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(session));
        when(session.getVersion()).thenReturn(9L);
        when(session.getMode()).thenReturn(SessionMode.VOICE_TURN_BASED);
        when(session.getStatus()).thenReturn(SessionStatus.IN_PROGRESS);
        when(session.getAwaitingAction()).thenReturn(AwaitingAction.CANDIDATE_ANSWER);
        when(session.getCurrentQuestionOrdinal()).thenReturn((short) 3);
        when(session.getCurrentFollowupDepth()).thenReturn((short) 0);
        when(session.getNextTurnIndex()).thenReturn(6);
        when(turnRepository.findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(
                        SESSION_ID, USER_ID))
                .thenReturn(Optional.of(prompt));
        when(prompt.getId()).thenReturn(PROMPT_ID);
        when(prompt.getRole()).thenReturn(TurnRole.INTERVIEWER);
        when(prompt.getQuestion()).thenReturn(question);
        when(prompt.getTurnIndex()).thenReturn(5);
        when(question.getId()).thenReturn(QUESTION_ID);
        when(question.getOrdinal()).thenReturn((short) 3);
    }

    private void sameExistingRequest() {
        when(existing.getPromptTurn()).thenReturn(prompt);
        when(prompt.getId()).thenReturn(PROMPT_ID);
        when(existing.getClientAttemptId()).thenReturn("attempt-1");
        when(existing.getFormat()).thenReturn(AudioFormat.WEBM_OPUS);
        when(existing.getFileSizeBytes()).thenReturn(100L);
        when(existing.getDurationMs()).thenReturn(5_000);
        when(existing.getChecksumSha256()).thenReturn("a".repeat(64));
    }

    private VoiceAttemptDraft draft() {
        return new VoiceAttemptDraft(
                PROMPT_ID,
                "attempt-1",
                9L,
                "key.webm",
                AudioFormat.WEBM_OPUS,
                100L,
                5_000,
                "a".repeat(64));
    }
}
