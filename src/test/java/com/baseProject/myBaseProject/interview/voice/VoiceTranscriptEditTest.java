package com.baseProject.myBaseProject.interview.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;
import com.baseProject.myBaseProject.enums.AudioFormat;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowDispatcher;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class VoiceTranscriptEditTest {

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

    @Test
    void normalizedEditEqualToRawIsStoredAsNullWithoutOverwritingRaw() {
        when(prompt.getQuestion()).thenReturn(question);
        VoiceAnswerAttempt attempt = VoiceAnswerAttempt.recorded(
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
        attempt.completeTranscription("Nội dung gốc", "gemini-test", null, NOW.minusSeconds(2));
        ReflectionTestUtils.setField(attempt, "id", 301L);
        ReflectionTestUtils.setField(attempt, "version", 2L);

        when(sessionRepository.findOwnedByIdForUpdate(42L, 7L))
                .thenReturn(Optional.of(session));
        when(attemptRepository.findOwnedByIdForUpdate(301L, 42L, 7L))
                .thenReturn(Optional.of(attempt));
        when(session.getStatus()).thenReturn(SessionStatus.IN_PROGRESS);
        when(session.getAwaitingAction()).thenReturn(AwaitingAction.TRANSCRIPT_CONFIRMATION);
        when(session.getCurrentQuestionOrdinal()).thenReturn((short) 3);
        when(session.getCurrentFollowupDepth()).thenReturn((short) 0);
        when(session.getNextTurnIndex()).thenReturn(6);
        when(turnRepository.findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(42L, 7L))
                .thenReturn(Optional.of(prompt));
        when(prompt.getId()).thenReturn(205L);
        when(prompt.getRole()).thenReturn(TurnRole.INTERVIEWER);
        when(prompt.getTurnIndex()).thenReturn(5);
        when(question.getOrdinal()).thenReturn((short) 3);

        VoiceAnswerAttempt edited = new VoiceAttemptStore(
                sessionRepository,
                turnRepository,
                attemptRepository,
                workflowDispatcher,
                Clock.fixed(NOW, ZoneOffset.UTC))
                .editTranscript(7L, 42L, 301L, "  Nội dung gốc  ", 2L);

        assertThat(edited.getRawText()).isEqualTo("Nội dung gốc");
        assertThat(edited.getEditedText()).isNull();
        verify(session).recordTranscriptEdit(NOW);
    }
}
