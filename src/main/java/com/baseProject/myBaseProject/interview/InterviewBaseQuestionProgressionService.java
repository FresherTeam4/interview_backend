package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionQuestionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewBaseQuestionProgressionService {

    private static final String COMPLETED_TRANSITION_REASON =
            "All base questions answered";

    private final InterviewSessionRepository sessionRepository;
    private final SessionQuestionRepository questionRepository;
    private final SessionTurnRepository turnRepository;
    private final SessionStateMachine stateMachine;
    private final Clock clock;

    @Transactional
    public ProgressionOutcome progress(Long sessionId, UUID processingToken) {
        InterviewSession session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (!ownsNextTurnClaim(session, processingToken)) {
            return ProgressionOutcome.IGNORED;
        }

        SessionTurn candidateTurn = turnRepository
                .findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(
                        sessionId,
                        session.getUser().getId())
                .orElseThrow(() -> inconsistent(sessionId, "candidate turn is missing"));
        if (candidateTurn.getRole() != TurnRole.CANDIDATE
                || candidateTurn.getQuestion() == null
                || session.getCurrentQuestionOrdinal() == null
                || candidateTurn.getTurnIndex() != session.getNextTurnIndex() - 1
                || session.getAnsweredQuestionCount()
                        != session.getCurrentQuestionOrdinal()
                || candidateTurn.getQuestion().getOrdinal()
                        != session.getCurrentQuestionOrdinal()) {
            throw inconsistent(sessionId, "latest turn is not the pending base answer");
        }

        if (session.getAnsweredQuestionCount() == session.getTotalQuestionCount()) {
            stateMachine.completeAllQuestions(
                    session,
                    processingToken,
                    COMPLETED_TRANSITION_REASON);
            return ProgressionOutcome.SCORING;
        }

        short nextOrdinal = (short) (session.getCurrentQuestionOrdinal() + 1);
        SessionQuestion nextQuestion = questionRepository.findBySessionIdAndOrdinal(
                        sessionId,
                        nextOrdinal)
                .orElseThrow(() -> inconsistent(sessionId, "next base question is missing"));
        Instant now = clock.instant();
        int turnIndex = session.advanceToBaseQuestion(nextOrdinal, processingToken, now);
        turnRepository.save(SessionTurn.nextBaseQuestionPrompt(
                session,
                nextQuestion,
                turnIndex,
                now));
        sessionRepository.saveAndFlush(session);
        return ProgressionOutcome.NEXT_QUESTION;
    }

    private boolean ownsNextTurnClaim(
            InterviewSession session,
            UUID processingToken) {
        return session != null
                && session.getStatus() == SessionStatus.IN_PROGRESS
                && session.getAwaitingAction() == AwaitingAction.ENGINE_RESPONSE
                && session.getProcessingStage() == SessionProcessingStage.NEXT_TURN
                && processingToken != null
                && processingToken.toString().equals(session.getProcessingToken());
    }

    private IllegalStateException inconsistent(Long sessionId, String detail) {
        return new IllegalStateException(
                "Next-turn persistence is inconsistent, sessionId=" + sessionId + ", " + detail);
    }

    public enum ProgressionOutcome {
        NEXT_QUESTION,
        SCORING,
        IGNORED
    }
}
