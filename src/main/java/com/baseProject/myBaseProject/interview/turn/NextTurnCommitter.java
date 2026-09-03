package com.baseProject.myBaseProject.interview.turn;

import static com.baseProject.myBaseProject.constant.InterviewConstraints.MAX_FOLLOW_UPS_PER_QUESTION;
import static com.baseProject.myBaseProject.constant.InterviewConstraints.MAX_FOLLOW_UPS_PER_SESSION;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.FollowUpDecision;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.interview.lifecycle.SessionStateMachine;
import com.baseProject.myBaseProject.interview.turn.model.NextTurnOutcome;
import com.baseProject.myBaseProject.interview.turn.model.ValidatedFollowUpDecision;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionQuestionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NextTurnCommitter {

    private static final String COMPLETED_TRANSITION_REASON = "All base questions answered";

    private final InterviewSessionRepository sessionRepository;
    private final SessionQuestionRepository questionRepository;
    private final SessionTurnRepository turnRepository;
    private final SessionStateMachine stateMachine;
    private final Clock clock;

    @Transactional
    public NextTurnOutcome commit(
            Long sessionId,
            UUID processingToken,
            Long expectedCandidateTurnId,
            Long expectedQuestionId,
            ValidatedFollowUpDecision requestedDecision,
            int providerDurationMs) {
        InterviewSession session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (!ownsNextTurnClaim(session, processingToken)) {
            return NextTurnOutcome.IGNORED;
        }

        SessionTurn candidateTurn = turnRepository
                .findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(
                        sessionId,
                        session.getUser().getId())
                .orElseThrow(() -> inconsistent(sessionId, "candidate turn is missing"));
        validatePendingCandidate(session, candidateTurn, sessionId);
        if (!Objects.equals(candidateTurn.getId(), expectedCandidateTurnId)
                || !Objects.equals(candidateTurn.getQuestion().getId(), expectedQuestionId)) {
            throw inconsistent(sessionId, "pending candidate changed during provider call");
        }

        boolean budgetAvailable = session.getCurrentFollowupDepth()
                        < MAX_FOLLOW_UPS_PER_QUESTION
                && session.getTotalFollowupCount() < MAX_FOLLOW_UPS_PER_SESSION;
        if (requestedDecision.decision() == FollowUpDecision.FOLLOW_UP && budgetAvailable) {
            short nextDepth = (short) (session.getCurrentFollowupDepth() + 1);
            Instant now = clock.instant();
            int turnIndex = session.advanceToFollowUp(nextDepth, processingToken, now);
            turnRepository.save(SessionTurn.followUpPrompt(
                    session,
                    candidateTurn.getQuestion(),
                    candidateTurn,
                    turnIndex,
                    requestedDecision.questionText(),
                    nextDepth,
                    providerDurationMs,
                    now));
            sessionRepository.saveAndFlush(session);
            return NextTurnOutcome.FOLLOW_UP;
        }

        if (session.getAnsweredQuestionCount() == session.getTotalQuestionCount()) {
            stateMachine.completeAllQuestions(
                    session,
                    processingToken,
                    COMPLETED_TRANSITION_REASON);
            return NextTurnOutcome.SCORING;
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
        return NextTurnOutcome.NEXT_QUESTION;
    }

    private void validatePendingCandidate(
            InterviewSession session,
            SessionTurn candidateTurn,
            Long sessionId) {
        if (candidateTurn.getRole() != TurnRole.CANDIDATE
                || candidateTurn.getQuestion() == null
                || session.getCurrentQuestionOrdinal() == null
                || candidateTurn.getTurnIndex() != session.getNextTurnIndex() - 1
                || session.getAnsweredQuestionCount()
                        != session.getCurrentQuestionOrdinal()
                || candidateTurn.getQuestion().getOrdinal()
                        != session.getCurrentQuestionOrdinal()) {
            throw inconsistent(sessionId, "latest turn is not the pending answer");
        }
    }

    private boolean ownsNextTurnClaim(
            InterviewSession session,
            UUID processingToken) {
        return session != null
                && session.getStatus() == SessionStatus.IN_PROGRESS
                && (session.getAwaitingAction() == AwaitingAction.ENGINE_RESPONSE
                        || session.getAwaitingAction() == AwaitingAction.ENGINE_RETRY)
                && session.getProcessingStage() == SessionProcessingStage.NEXT_TURN
                && processingToken != null
                && processingToken.toString().equals(session.getProcessingToken());
    }

    private IllegalStateException inconsistent(Long sessionId, String detail) {
        return new IllegalStateException(
                "Next-turn persistence is inconsistent, sessionId=" + sessionId + ", " + detail);
    }
}
