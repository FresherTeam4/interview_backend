package com.baseProject.myBaseProject.interview.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.dto.interview.InterviewSessionResponse;
import com.baseProject.myBaseProject.dto.interview.SessionVersionRequest;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.enums.VoiceAttemptStatus;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.mapper.InterviewSessionMapper;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionQuestionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class InterviewSessionLifecycleVoiceResumeTest {

    @Mock
    private SessionStateMachine stateMachine;
    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private SessionQuestionRepository questionRepository;
    @Mock
    private SessionTurnRepository turnRepository;
    @Mock
    private VoiceAnswerAttemptRepository voiceAttemptRepository;
    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewSession paused;
    @Mock
    private InterviewSession resumed;
    @Mock
    private SessionTurn prompt;
    @Mock
    private VoiceAnswerAttempt attempt;
    @Mock
    private InterviewSessionResponse response;

    @Test
    void resumeRestoresTranscriptConfirmationWhenCurrentPromptHasVoiceDraft() {
        when(sessionRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(paused));
        when(paused.getStatus()).thenReturn(SessionStatus.PAUSED);
        when(turnRepository.findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(42L, 7L))
                .thenReturn(Optional.of(prompt));
        when(prompt.getRole()).thenReturn(TurnRole.INTERVIEWER);
        when(prompt.getId()).thenReturn(205L);
        when(voiceAttemptRepository.findFirstBySessionIdAndPromptTurnIdOrderByAttemptNoDesc(
                        42L,
                        205L))
                .thenReturn(Optional.of(attempt));
        when(stateMachine.resume(
                        7L,
                        42L,
                        9L,
                        AwaitingAction.TRANSCRIPT_CONFIRMATION,
                        "User resumed interview"))
                .thenReturn(resumed);
        when(turnRepository.findOwnedHistory(42L, 7L)).thenReturn(List.of(prompt));
        when(sessionMapper.toResponse(resumed, List.of(prompt), attempt)).thenReturn(response);

        InterviewSessionResponse actual = new InterviewSessionLifecycleService(
                stateMachine,
                sessionRepository,
                questionRepository,
                turnRepository,
                voiceAttemptRepository,
                sessionMapper)
                .resume(7L, 42L, new SessionVersionRequest(9L));

        assertThat(actual).isSameAs(response);
        verify(stateMachine).resume(
                7L,
                42L,
                9L,
                AwaitingAction.TRANSCRIPT_CONFIRMATION,
                "User resumed interview");
    }

    @Test
    void pauseRollsBackWhileCurrentVoiceAttemptIsTranscribing() {
        when(stateMachine.pause(7L, 42L, 9L, "User paused interview"))
                .thenReturn(paused);
        when(turnRepository.findOwnedHistory(42L, 7L)).thenReturn(List.of(prompt));
        when(prompt.getRole()).thenReturn(TurnRole.INTERVIEWER);
        when(prompt.getId()).thenReturn(205L);
        when(voiceAttemptRepository.findFirstBySessionIdAndPromptTurnIdOrderByAttemptNoDesc(
                        42L,
                        205L))
                .thenReturn(Optional.of(attempt));
        when(attempt.getStatus()).thenReturn(VoiceAttemptStatus.TRANSCRIBING);

        InterviewSessionLifecycleService service = new InterviewSessionLifecycleService(
                stateMachine,
                sessionRepository,
                questionRepository,
                turnRepository,
                voiceAttemptRepository,
                sessionMapper);

        assertThatThrownBy(() -> service.pause(7L, 42L, new SessionVersionRequest(9L)))
                .isInstanceOf(SessionInvalidStateException.class);
    }
}
