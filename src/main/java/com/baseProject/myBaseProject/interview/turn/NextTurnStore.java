package com.baseProject.myBaseProject.interview.turn;

import static com.baseProject.myBaseProject.constant.InterviewConstraints.MAX_FOLLOW_UPS_PER_QUESTION;
import static com.baseProject.myBaseProject.constant.InterviewConstraints.MAX_FOLLOW_UPS_PER_SESSION;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionContextSnapshot;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.FollowUpDecision;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionContract.FollowUpDecisionInput;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionContract.FollowUpTurnContext;
import com.baseProject.myBaseProject.interview.lifecycle.SessionStateMachine;
import com.baseProject.myBaseProject.interview.turn.model.NextTurnData.NextTurnOutcome;
import com.baseProject.myBaseProject.interview.turn.model.NextTurnData.NextTurnPreparation;
import com.baseProject.myBaseProject.interview.turn.model.NextTurnData.ValidatedFollowUpDecision;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionContextSnapshotRepository;
import com.baseProject.myBaseProject.repository.SessionQuestionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Transactional persistence boundary for adaptive next-turn decisions. */
@Component
@RequiredArgsConstructor
public class NextTurnStore {

    private static final int MAX_CURRENT_QUESTION_TURNS =
            2 + MAX_FOLLOW_UPS_PER_QUESTION * 2;
    private static final String COMPLETED_TRANSITION_REASON = "All base questions answered";

    private final InterviewSessionRepository sessionRepository;
    private final SessionContextSnapshotRepository snapshotRepository;
    private final SessionQuestionRepository questionRepository;
    private final SessionTurnRepository turnRepository;
    private final SessionStateMachine stateMachine;
    private final Clock clock;

    @Transactional(readOnly = true)
    public NextTurnPreparation prepare(Long sessionId, UUID processingToken) {
        InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (!ownsNextTurnClaim(session, processingToken)) {
            return null;
        }

        SessionTurn candidateTurn = latestCandidateTurn(session, sessionId);
        validatePendingCandidate(session, candidateTurn, sessionId);
        SessionQuestion question = candidateTurn.getQuestion();
        List<SessionTurn> currentTurns = turnRepository
                .findBySessionIdAndQuestionIdOrderByTurnIndexAsc(sessionId, question.getId());
        if (currentTurns.isEmpty() || currentTurns.size() > MAX_CURRENT_QUESTION_TURNS) {
            throw inconsistent(sessionId, "current question context is not bounded");
        }

        SessionContextSnapshot snapshot = snapshotRepository.findBySessionId(sessionId)
                .orElseThrow(SessionNotFoundException::new);
        FollowUpDecisionInput input = new FollowUpDecisionInput(
                session.getLanguageCode(),
                question.getQuestionText(),
                question.getTopic(),
                question.getCompetency(),
                relevantProfileContext(snapshot.getProfileJson(), question),
                question.getSourceJdExcerpt(),
                currentTurns.stream()
                        .map(turn -> new FollowUpTurnContext(
                                turn.getRole(),
                                turn.getContentText(),
                                turn.isFollowUp(),
                                turn.getFollowUpDepth()))
                        .toList(),
                candidateTurn.getContentText(),
                Math.max(
                        0,
                        MAX_FOLLOW_UPS_PER_QUESTION
                                - session.getCurrentFollowupDepth()),
                Math.max(
                        0,
                        MAX_FOLLOW_UPS_PER_SESSION
                                - session.getTotalFollowupCount()));
        return new NextTurnPreparation(candidateTurn.getId(), question.getId(), input);
    }

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

        SessionTurn candidateTurn = latestCandidateTurn(session, sessionId);
        validatePendingCandidate(session, candidateTurn, sessionId);
        if (!Objects.equals(candidateTurn.getId(), expectedCandidateTurnId)
                || !Objects.equals(candidateTurn.getQuestion().getId(), expectedQuestionId)) {
            throw inconsistent(sessionId, "pending candidate changed during provider call");
        }

        boolean budgetAvailable = session.getCurrentFollowupDepth()
                        < MAX_FOLLOW_UPS_PER_QUESTION
                && session.getTotalFollowupCount() < MAX_FOLLOW_UPS_PER_SESSION;
        if (requestedDecision.decision() == FollowUpDecision.FOLLOW_UP && budgetAvailable) {
            return persistFollowUp(
                    session,
                    candidateTurn,
                    processingToken,
                    requestedDecision,
                    providerDurationMs);
        }

        if (session.getAnsweredQuestionCount() == session.getTotalQuestionCount()) {
            stateMachine.completeAllQuestions(
                    session, processingToken, COMPLETED_TRANSITION_REASON);
            return NextTurnOutcome.SCORING;
        }

        persistNextBaseQuestion(session, processingToken, sessionId);
        return NextTurnOutcome.NEXT_QUESTION;
    }

    private NextTurnOutcome persistFollowUp(
            InterviewSession session,
            SessionTurn candidateTurn,
            UUID processingToken,
            ValidatedFollowUpDecision decision,
            int providerDurationMs) {
        short nextDepth = (short) (session.getCurrentFollowupDepth() + 1);
        Instant now = clock.instant();
        int turnIndex = session.advanceToFollowUp(nextDepth, processingToken, now);
        turnRepository.save(SessionTurn.followUpPrompt(
                session,
                candidateTurn.getQuestion(),
                candidateTurn,
                turnIndex,
                decision.questionText(),
                nextDepth,
                providerDurationMs,
                now));
        sessionRepository.saveAndFlush(session);
        return NextTurnOutcome.FOLLOW_UP;
    }

    private void persistNextBaseQuestion(
            InterviewSession session,
            UUID processingToken,
            Long sessionId) {
        short nextOrdinal = (short) (session.getCurrentQuestionOrdinal() + 1);
        SessionQuestion nextQuestion = questionRepository.findBySessionIdAndOrdinal(
                        sessionId, nextOrdinal)
                .orElseThrow(() -> inconsistent(sessionId, "next base question is missing"));
        Instant now = clock.instant();
        int turnIndex = session.advanceToBaseQuestion(nextOrdinal, processingToken, now);
        turnRepository.save(SessionTurn.nextBaseQuestionPrompt(
                session, nextQuestion, turnIndex, now));
        sessionRepository.saveAndFlush(session);
    }

    private SessionTurn latestCandidateTurn(
            InterviewSession session,
            Long sessionId) {
        return turnRepository
                .findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(
                        sessionId, session.getUser().getId())
                .orElseThrow(() -> inconsistent(sessionId, "candidate turn is missing"));
    }

    private JsonNode relevantProfileContext(JsonNode profile, SessionQuestion question) {
        ObjectNode relevant = JsonNodeFactory.instance.objectNode();
        if (question.getSourceProjectSnapshotId() != null) {
            copyMatchingItem(
                    profile.path("projects"),
                    question.getSourceProjectSnapshotId(),
                    relevant,
                    "project");
        }
        if (question.getSourceSkillSnapshotId() != null) {
            copyMatchingItem(
                    profile.path("skills"),
                    question.getSourceSkillSnapshotId(),
                    relevant,
                    "skill");
        }
        return relevant;
    }

    private void copyMatchingItem(
            JsonNode items,
            Long expectedId,
            ObjectNode target,
            String fieldName) {
        if (!items.isArray() || expectedId == null) {
            return;
        }
        for (JsonNode item : items) {
            if (item.path("id").canConvertToLong()
                    && item.path("id").longValue() == expectedId) {
                target.set(fieldName, item.deepCopy());
                return;
            }
        }
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
