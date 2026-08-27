package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.config.properites.InterviewAiProperties;
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
import com.baseProject.myBaseProject.interview.ai.FollowUpDecisionInput;
import com.baseProject.myBaseProject.interview.ai.FollowUpDecisionOutcome;
import com.baseProject.myBaseProject.interview.ai.FollowUpTurnContext;
import com.baseProject.myBaseProject.interview.ai.InterviewFollowUpDecider;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionContextSnapshotRepository;
import com.baseProject.myBaseProject.repository.SessionQuestionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class InterviewAdaptiveNextTurnService {

    private static final short MAX_FOLLOW_UPS_PER_QUESTION = 2;
    private static final short MAX_FOLLOW_UPS_PER_SESSION = 5;
    private static final int MAX_CURRENT_QUESTION_TURNS = 6;
    private static final String COMPLETED_TRANSITION_REASON = "All base questions answered";

    private final InterviewFollowUpDecider followUpDecider;
    private final FollowUpDecisionValidator decisionValidator;
    private final InterviewAiProperties aiProperties;
    private final InterviewSessionRepository sessionRepository;
    private final SessionContextSnapshotRepository snapshotRepository;
    private final SessionQuestionRepository questionRepository;
    private final SessionTurnRepository turnRepository;
    private final SessionStateMachine stateMachine;
    private final Clock clock;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public InterviewAdaptiveNextTurnService(
            InterviewFollowUpDecider followUpDecider,
            FollowUpDecisionValidator decisionValidator,
            InterviewAiProperties aiProperties,
            InterviewSessionRepository sessionRepository,
            SessionContextSnapshotRepository snapshotRepository,
            SessionQuestionRepository questionRepository,
            SessionTurnRepository turnRepository,
            SessionStateMachine stateMachine,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.followUpDecider = followUpDecider;
        this.decisionValidator = decisionValidator;
        this.aiProperties = aiProperties;
        this.sessionRepository = sessionRepository;
        this.snapshotRepository = snapshotRepository;
        this.questionRepository = questionRepository;
        this.turnRepository = turnRepository;
        this.stateMachine = stateMachine;
        this.clock = clock;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    /** Loads bounded context, calls AI outside a transaction, then atomically commits its decision. */
    public NextTurnOutcome decideAndPersist(Long sessionId, UUID processingToken) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(processingToken);
        NextTurnPreparation preparation = loadPreparation(sessionId, processingToken);
        if (preparation == null) {
            return NextTurnOutcome.IGNORED;
        }

        ValidatedFollowUpDecision decision;
        int providerDurationMs = 0;
        FollowUpDecisionInput input = preparation.input();
        if (input.remainingQuestionFollowUps() == 0
                || input.remainingSessionFollowUps() == 0) {
            decision = ValidatedFollowUpDecision.nextQuestion(true);
        } else {
            FollowUpDecisionOutcome outcome = followUpDecider.decide(input);
            decision = decisionValidator.validate(
                    outcome,
                    input,
                    aiProperties.followUpPromptVersion());
            providerDurationMs = outcome.durationMs();
            log.info(
                    "Interview follow-up provider call completed: sessionId={}, model={}, "
                            + "promptVersion={}, durationMs={}, tokenCost={}",
                    sessionId,
                    outcome.modelName(),
                    outcome.promptVersion(),
                    outcome.durationMs(),
                    outcome.tokenCost());
        }

        NextTurnOutcome result = persist(
                sessionId,
                processingToken,
                preparation.candidateTurnId(),
                preparation.questionId(),
                decision,
                providerDurationMs);
        log.info(
                "Interview next-turn decision persisted: sessionId={}, outcome={}, "
                        + "serverOverridden={}",
                sessionId,
                result,
                decision.serverOverridden());
        return result;
    }

    private NextTurnPreparation loadPreparation(Long sessionId, UUID processingToken) {
        return readTransaction.execute(status -> {
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
        });
    }

    private NextTurnOutcome persist(
            Long sessionId,
            UUID processingToken,
            Long expectedCandidateTurnId,
            Long expectedQuestionId,
            ValidatedFollowUpDecision requestedDecision,
            int providerDurationMs) {
        return Objects.requireNonNull(writeTransaction.execute(status -> {
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
        }));
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

    public enum NextTurnOutcome {
        FOLLOW_UP,
        NEXT_QUESTION,
        SCORING,
        IGNORED
    }

    private record NextTurnPreparation(
            Long candidateTurnId,
            Long questionId,
            FollowUpDecisionInput input) {
    }
}
