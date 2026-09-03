package com.baseProject.myBaseProject.interview.turn;

import static com.baseProject.myBaseProject.constant.InterviewConstraints.MAX_FOLLOW_UPS_PER_QUESTION;
import static com.baseProject.myBaseProject.constant.InterviewConstraints.MAX_FOLLOW_UPS_PER_SESSION;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionContextSnapshot;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionInput;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpTurnContext;
import com.baseProject.myBaseProject.interview.turn.model.NextTurnPreparation;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionContextSnapshotRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NextTurnPreparationReader {

    private static final int MAX_CURRENT_QUESTION_TURNS =
            2 + MAX_FOLLOW_UPS_PER_QUESTION * 2;

    private final InterviewSessionRepository sessionRepository;
    private final SessionContextSnapshotRepository snapshotRepository;
    private final SessionTurnRepository turnRepository;

    @Transactional(readOnly = true)
    public NextTurnPreparation prepare(Long sessionId, UUID processingToken) {
        InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (!ownsNextTurnClaim(session, processingToken)) {
            return null;
        }

        SessionTurn candidateTurn = turnRepository
                .findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(
                        sessionId,
                        session.getUser().getId())
                .orElseThrow(() -> inconsistent(sessionId, "candidate turn is missing"));
        validatePendingCandidate(session, candidateTurn, sessionId);
        SessionQuestion question = candidateTurn.getQuestion();
        List<SessionTurn> currentTurns = turnRepository
                .findBySessionIdAndQuestionIdOrderByTurnIndexAsc(sessionId, question.getId());
        if (currentTurns.isEmpty() || currentTurns.size() > MAX_CURRENT_QUESTION_TURNS) {
            throw inconsistent(sessionId, "current question context is not bounded");
        }

        SessionContextSnapshot snapshot = snapshotRepository.findBySessionId(sessionId)
                .orElseThrow(SessionNotFoundException::new);
        int remainingQuestionFollowUps = Math.max(
                0,
                MAX_FOLLOW_UPS_PER_QUESTION - session.getCurrentFollowupDepth());
        int remainingSessionFollowUps = Math.max(
                0,
                MAX_FOLLOW_UPS_PER_SESSION - session.getTotalFollowupCount());
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
                remainingQuestionFollowUps,
                remainingSessionFollowUps);
        return new NextTurnPreparation(
                candidateTurn.getId(),
                question.getId(),
                input);
    }

    private JsonNode relevantProfileContext(JsonNode profile, SessionQuestion question) {
        ObjectNode relevant = JsonNodeFactory.instance.objectNode();
        if (question.getSourceProject() != null) {
            copyMatchingItem(
                    profile.path("projects"),
                    question.getSourceProject().getId(),
                    relevant,
                    "project");
        }
        if (question.getSourceSkill() != null) {
            copyMatchingItem(
                    profile.path("skills"),
                    question.getSourceSkill().getId(),
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
