package com.baseProject.myBaseProject.interview.turn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.dto.interview.SubmitTextAnswerRequest;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;
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
class InterviewAnswerServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private SessionTurnRepository turnRepository;
    @Mock
    private VoiceAnswerAttemptRepository voiceAttemptRepository;
    @Mock
    private InterviewWorkflowDispatcher workflowDispatcher;
    @Mock
    private InterviewSession session;
    @Mock
    private SessionTurn prompt;
    @Mock
    private SessionQuestion question;
    @Mock
    private VoiceAnswerAttempt voiceAttempt;

    @Test
    void voiceSessionCanUseTextFallbackWithoutLosingCurrentPromptContext() {
        when(sessionRepository.findOwnedByIdForUpdate(42L, 7L))
                .thenReturn(Optional.of(session));
        when(session.getVersion()).thenReturn(9L);
        when(session.getMode()).thenReturn(SessionMode.VOICE_TURN_BASED);
        when(session.getStatus()).thenReturn(SessionStatus.IN_PROGRESS);
        when(session.getAwaitingAction()).thenReturn(AwaitingAction.TRANSCRIPT_CONFIRMATION);
        when(session.getCurrentQuestionOrdinal()).thenReturn((short) 3);
        when(session.getCurrentFollowupDepth()).thenReturn((short) 0);
        when(session.getNextTurnIndex()).thenReturn(6);
        when(session.acceptBaseQuestionAnswer(any(), any())).thenReturn(6);
        when(turnRepository.findBySessionIdAndClientTurnId(42L, "turn-text"))
                .thenReturn(Optional.empty());
        when(turnRepository.findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(42L, 7L))
                .thenReturn(Optional.of(prompt));
        when(prompt.getId()).thenReturn(205L);
        when(prompt.getRole()).thenReturn(TurnRole.INTERVIEWER);
        when(prompt.getQuestion()).thenReturn(question);
        when(prompt.getTurnIndex()).thenReturn(5);
        when(question.getOrdinal()).thenReturn((short) 3);
        when(turnRepository.save(any(SessionTurn.class))).thenAnswer(invocation -> {
            SessionTurn turn = invocation.getArgument(0);
            ReflectionTestUtils.setField(turn, "id", 501L);
            return turn;
        });
        when(voiceAttemptRepository.findPromptAttemptsForUpdate(42L, 205L))
                .thenReturn(List.of(voiceAttempt));
        when(voiceAttempt.getStatus()).thenReturn(VoiceAttemptStatus.TRANSCRIBED);

        InterviewAnswerService service = new InterviewAnswerService(
                new InterviewProperties(true, 24, 90, 5, 10_000),
                sessionRepository,
                turnRepository,
                voiceAttemptRepository,
                workflowDispatcher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        service.submitTextAnswer(
                7L,
                42L,
                new SubmitTextAnswerRequest(205L, "Câu trả lời text", "turn-text", 9L));

        ArgumentCaptor<SessionTurn> candidate = ArgumentCaptor.forClass(SessionTurn.class);
        verify(turnRepository).save(candidate.capture());
        assertThat(candidate.getValue().getInputMode()).isEqualTo(TurnInputMode.TEXT);
        assertThat(candidate.getValue().getQuestion()).isSameAs(question);
        verify(voiceAttempt).discard();
        verify(voiceAttemptRepository).saveAll(List.of(voiceAttempt));
    }
}
