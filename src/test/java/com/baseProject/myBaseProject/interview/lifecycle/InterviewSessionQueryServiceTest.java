package com.baseProject.myBaseProject.interview.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.dto.interview.InterviewSessionResponse;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.mapper.InterviewSessionMapper;
import com.baseProject.myBaseProject.mapper.RubricMapper;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class InterviewSessionQueryServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 42L;
    private static final Long PROMPT_ID = 205L;

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private SessionTurnRepository turnRepository;
    @Mock
    private VoiceAnswerAttemptRepository voiceAttemptRepository;
    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private RubricMapper rubricMapper;
    @Mock
    private InterviewSession session;
    @Mock
    private SessionTurn prompt;
    @Mock
    private VoiceAnswerAttempt voiceDraft;
    @Mock
    private InterviewSessionResponse response;

    private InterviewSessionQueryService service;

    @BeforeEach
    void setUp() {
        service = new InterviewSessionQueryService(
                sessionRepository,
                turnRepository,
                voiceAttemptRepository,
                sessionMapper,
                rubricMapper);
    }

    @Test
    void getIncludesLatestVoiceDraftForCurrentPrompt() {
        List<SessionTurn> turns = List.of(prompt);
        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(session));
        when(turnRepository.findOwnedHistory(SESSION_ID, USER_ID)).thenReturn(turns);
        when(prompt.getRole()).thenReturn(TurnRole.INTERVIEWER);
        when(prompt.getId()).thenReturn(PROMPT_ID);
        when(voiceAttemptRepository
                        .findFirstBySessionIdAndPromptTurnIdOrderByAttemptNoDesc(
                                SESSION_ID,
                                PROMPT_ID))
                .thenReturn(Optional.of(voiceDraft));
        when(sessionMapper.toResponse(session, turns, voiceDraft)).thenReturn(response);

        assertThat(service.get(USER_ID, SESSION_ID)).isSameAs(response);

        verify(sessionMapper).toResponse(session, turns, voiceDraft);
    }
}
