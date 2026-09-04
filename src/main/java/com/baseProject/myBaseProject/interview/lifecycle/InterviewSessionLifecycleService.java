package com.baseProject.myBaseProject.interview.lifecycle;

import com.baseProject.myBaseProject.dto.interview.InterviewSessionResponse;
import com.baseProject.myBaseProject.dto.interview.SessionVersionRequest;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.mapper.InterviewSessionMapper;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionQuestionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewSessionLifecycleService {

    private static final String START_TRANSITION_REASON = "User started interview";
    private static final String PAUSE_TRANSITION_REASON = "User paused interview";
    private static final String RESUME_TRANSITION_REASON = "User resumed interview";

    private final SessionStateMachine stateMachine;
    private final InterviewSessionRepository sessionRepository;
    private final SessionQuestionRepository questionRepository;
    private final SessionTurnRepository turnRepository;
    private final VoiceAnswerAttemptRepository voiceAttemptRepository;
    private final InterviewSessionMapper sessionMapper;

    @Transactional
    public InterviewSessionResponse start(
            Long userId,
            Long sessionId,
            SessionVersionRequest request) {
        InterviewSession started = stateMachine.start(
                userId,
                sessionId,
                request.expectedVersion(),
                START_TRANSITION_REASON);
        SessionQuestion firstQuestion = questionRepository.findBySessionIdAndOrdinal(
                        sessionId, (short) 1)
                .orElseThrow(() -> new IllegalStateException(
                        "READY session has no first question, sessionId=" + sessionId));
        int turnIndex = started.beginAtQuestion(firstQuestion.getOrdinal());
        SessionTurn firstPrompt = turnRepository.save(SessionTurn.firstInterviewerPrompt(
                started,
                firstQuestion,
                turnIndex,
                started.getStartedAt()));
        sessionRepository.saveAndFlush(started);
        return sessionMapper.toResponse(started, List.of(firstPrompt));
    }

    @Transactional
    public InterviewSessionResponse pause(
            Long userId,
            Long sessionId,
            SessionVersionRequest request) {
        InterviewSession paused = stateMachine.pause(
                userId,
                sessionId,
                request.expectedVersion(),
                PAUSE_TRANSITION_REASON);
        List<SessionTurn> turns = turnRepository.findOwnedHistory(sessionId, userId);
        return sessionMapper.toResponse(paused, turns, latestVoiceDraft(sessionId, turns));
    }

    @Transactional
    public InterviewSessionResponse resume(
            Long userId,
            Long sessionId,
            SessionVersionRequest request) {
        AwaitingAction restoredAction = resolveAwaitingAction(userId, sessionId);
        InterviewSession resumed = stateMachine.resume(
                userId,
                sessionId,
                request.expectedVersion(),
                restoredAction,
                RESUME_TRANSITION_REASON);
        List<SessionTurn> turns = turnRepository.findOwnedHistory(sessionId, userId);
        return sessionMapper.toResponse(resumed, turns, latestVoiceDraft(sessionId, turns));
    }

    private AwaitingAction resolveAwaitingAction(Long userId, Long sessionId) {
        InterviewSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.PAUSED) {
            throw new SessionInvalidStateException();
        }
        SessionTurn latestTurn = turnRepository
                .findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(sessionId, userId)
                .orElseThrow(SessionInvalidStateException::new);
        if (latestTurn.getRole() != TurnRole.INTERVIEWER) {
            throw new SessionInvalidStateException();
        }
        return AwaitingAction.CANDIDATE_ANSWER;
    }

    private VoiceAnswerAttempt latestVoiceDraft(
            Long sessionId,
            List<SessionTurn> turns) {
        return turns.stream()
                .filter(turn -> turn.getRole() == TurnRole.INTERVIEWER)
                .reduce((first, second) -> second)
                .flatMap(turn -> voiceAttemptRepository
                        .findFirstBySessionIdAndPromptTurnIdOrderByAttemptNoDesc(
                                sessionId,
                                turn.getId()))
                .orElse(null);
    }
}
