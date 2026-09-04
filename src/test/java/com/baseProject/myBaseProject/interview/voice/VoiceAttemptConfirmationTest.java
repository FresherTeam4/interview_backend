package com.baseProject.myBaseProject.interview.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.dto.interview.TextAnswerAcceptedResponse;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;
import com.baseProject.myBaseProject.enums.AudioFormat;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionMode;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnInputMode;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.enums.VoiceAttemptStatus;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowDispatcher;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class VoiceAttemptConfirmationTest {

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

    private VoiceAttemptStore store;
    private VoiceAnswerAttempt attempt;

    @BeforeEach
    void setUp() {
        store = new VoiceAttemptStore(
                sessionRepository,
                turnRepository,
                attemptRepository,
                workflowDispatcher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(prompt.getQuestion()).thenReturn(question);
        attempt = VoiceAnswerAttempt.recorded(
                session,
                prompt,
                (short) 1,
                "attempt-1",
                "key.webm",
                AudioFormat.WEBM_OPUS,
                100,
                5_000,
                "a".repeat(64),
                NOW.minusSeconds(5));
        attempt.completeTranscription("Bản raw", "gemini-test", null, NOW.minusSeconds(2));
        ReflectionTestUtils.setField(attempt, "id", 301L);
        ReflectionTestUtils.setField(attempt, "version", 2L);

        when(sessionRepository.findOwnedByIdForUpdate(42L, 7L))
                .thenReturn(Optional.of(session));
        when(attemptRepository.findOwnedByIdForUpdate(301L, 42L, 7L))
                .thenReturn(Optional.of(attempt));
        when(session.getId()).thenReturn(42L);
        when(session.getVersion()).thenReturn(9L);
        when(session.getMode()).thenReturn(SessionMode.VOICE_TURN_BASED);
        when(session.getStatus()).thenReturn(SessionStatus.IN_PROGRESS);
        when(session.getAwaitingAction()).thenReturn(AwaitingAction.TRANSCRIPT_CONFIRMATION);
        when(session.getCurrentQuestionOrdinal()).thenReturn((short) 3);
        when(session.getCurrentFollowupDepth()).thenReturn((short) 0);
        when(session.getNextTurnIndex()).thenReturn(6);
        when(session.acceptBaseQuestionAnswer(any(), any())).thenReturn(6);
        when(turnRepository.findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(42L, 7L))
                .thenReturn(Optional.of(prompt));
        when(prompt.getId()).thenReturn(205L);
        when(prompt.getRole()).thenReturn(TurnRole.INTERVIEWER);
        when(prompt.getTurnIndex()).thenReturn(5);
        when(question.getOrdinal()).thenReturn((short) 3);
        when(turnRepository.findBySessionIdAndClientTurnId(42L, "turn-1"))
                .thenReturn(Optional.empty());
        when(turnRepository.save(any(SessionTurn.class))).thenAnswer(invocation -> {
            SessionTurn turn = invocation.getArgument(0);
            ReflectionTestUtils.setField(turn, "id", 501L);
            return turn;
        });
        when(attemptRepository.findPromptAttemptsForUpdate(42L, 205L))
                .thenReturn(List.of(attempt));
    }

    @Test
    void rawTranscriptCreatesVoiceCandidateOnlyOnConfirm() {
        TextAnswerAcceptedResponse response = store.confirm(
                7L,
                42L,
                301L,
                "turn-1",
                9L,
                2L);

        ArgumentCaptor<SessionTurn> candidate = ArgumentCaptor.forClass(SessionTurn.class);
        verify(turnRepository).save(candidate.capture());
        assertThat(candidate.getValue().getContentText()).isEqualTo("Bản raw");
        assertThat(candidate.getValue().getInputMode()).isEqualTo(TurnInputMode.VOICE_TURN_BASED);
        assertThat(attempt.getStatus()).isEqualTo(VoiceAttemptStatus.CONFIRMED);
        assertThat(attempt.getConfirmedTurn()).isSameAs(candidate.getValue());
        assertThat(response.candidateTurnId()).isEqualTo(501L);
    }

    @Test
    void editedTranscriptIsTheFinalCandidateContent() {
        attempt.editTranscript("Bản đã sửa");

        store.confirm(7L, 42L, 301L, "turn-1", 9L, 2L);

        ArgumentCaptor<SessionTurn> candidate = ArgumentCaptor.forClass(SessionTurn.class);
        verify(turnRepository).save(candidate.capture());
        assertThat(candidate.getValue().getContentText()).isEqualTo("Bản đã sửa");
        assertThat(attempt.getRawText()).isEqualTo("Bản raw");
    }

    @Test
    void repeatedConfirmDoesNotCreateSecondCandidateTurn() {
        TextAnswerAcceptedResponse first = store.confirm(
                7L, 42L, 301L, "turn-1", 9L, 2L);
        TextAnswerAcceptedResponse replay = store.confirm(
                7L, 42L, 301L, "turn-1", 0L, 0L);

        assertThat(replay.candidateTurnId()).isEqualTo(first.candidateTurnId());
        verify(turnRepository, times(1)).save(any(SessionTurn.class));
    }
}
