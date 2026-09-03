package com.baseProject.myBaseProject.interview.lifecycle;

import com.baseProject.myBaseProject.interview.lifecycle.model.SessionEvent;
import com.baseProject.myBaseProject.interview.lifecycle.model.SessionStateChange;

import static com.baseProject.myBaseProject.constant.InterviewConstraints.MAX_BASE_QUESTION_COUNT;
import static com.baseProject.myBaseProject.constant.InterviewConstraints.MIN_BASE_QUESTION_COUNT;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionEndReason;
import com.baseProject.myBaseProject.enums.SessionFailureStage;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.exception.SessionRetryNotAllowedException;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class SessionTransitionPlanner {

    private static final int STATUS_MESSAGE_MAX_LENGTH = 500;
    private static final Set<AwaitingAction> IN_PROGRESS_ACTIONS = EnumSet.of(
            AwaitingAction.CANDIDATE_ANSWER,
            AwaitingAction.TRANSCRIPT_CONFIRMATION,
            AwaitingAction.ENGINE_RESPONSE,
            AwaitingAction.ENGINE_RETRY);
    private static final Map<SessionStatus, Set<AwaitingAction>> ALLOWED_AWAITING_ACTIONS =
            allowedAwaitingActions();

    public boolean isUserEvent(SessionEvent event) {
        return event == SessionEvent.START
                || event == SessionEvent.PAUSE
                || event == SessionEvent.RESUME
                || event == SessionEvent.COMPLETE_EARLY
                || event == SessionEvent.ABANDON
                || event == SessionEvent.RETRY;
    }

    public boolean isSystemEvent(SessionEvent event) {
        return event == SessionEvent.DISPATCH_SCRIPT_GENERATION
                || event == SessionEvent.SCRIPT_PERSISTED
                || event == SessionEvent.ALL_QUESTIONS_ANSWERED
                || event == SessionEvent.REPORT_COMMITTED
                || event == SessionEvent.WORKFLOW_FAILED;
    }

    public SessionStateChange userPlan(
            InterviewSession session,
            SessionEvent event,
            AwaitingAction restoredAwaitingAction) {
        return switch (event) {
            case START -> startPlan(session);
            case PAUSE -> pausePlan(session);
            case RESUME -> resumePlan(session, restoredAwaitingAction);
            case COMPLETE_EARLY -> scoringPlan(
                    session, SessionEndReason.USER_COMPLETED_EARLY, true);
            case ABANDON -> abandonPlan(session);
            case RETRY -> retryPlan(session);
            default -> throw invalidState();
        };
    }

    public SessionStateChange retryUserPlan(
            InterviewSession session,
            Set<SessionFailureStage> allowedFailureStages) {
        if (session.getStatus() == SessionStatus.IN_PROGRESS
                && session.getAwaitingAction() == AwaitingAction.ENGINE_RETRY
                && session.getProcessingStage() == SessionProcessingStage.NEXT_TURN
                && session.getProcessingToken() == null) {
            return nextTurnRetryPlan();
        }
        if (session.getStatus() != SessionStatus.FAILED
                || session.getFailureStage() == null
                || allowedFailureStages == null
                || !allowedFailureStages.contains(session.getFailureStage())) {
            throw new SessionRetryNotAllowedException();
        }
        return retryPlan(session);
    }

    public SessionStateChange systemPlan(
            InterviewSession session,
            SessionEvent event,
            UUID processingToken,
            SessionFailureStage failureStage,
            String statusMessage) {
        return switch (event) {
            case DISPATCH_SCRIPT_GENERATION -> dispatchGenerationPlan(session);
            case SCRIPT_PERSISTED -> scriptPersistedPlan(session, processingToken);
            case ALL_QUESTIONS_ANSWERED -> allQuestionsAnsweredPlan(session, processingToken);
            case REPORT_COMMITTED -> reportCommittedPlan(session, processingToken);
            case WORKFLOW_FAILED -> failurePlan(
                    session, processingToken, failureStage, statusMessage);
            default -> throw invalidState();
        };
    }

    public SessionStateChange scriptPersistedPlan(
            InterviewSession session,
            UUID processingToken) {
        requireStatus(session, SessionStatus.SCRIPT_GENERATING);
        verifyClaim(session, SessionProcessingStage.SCRIPT_GENERATION, processingToken);
        if (session.getTotalQuestionCount() < MIN_BASE_QUESTION_COUNT
                || session.getTotalQuestionCount() > MAX_BASE_QUESTION_COUNT) {
            throw invalidState();
        }
        return new SessionStateChange(
                SessionStatus.READY,
                AwaitingAction.START_SESSION,
                null,
                null,
                null,
                null,
                false,
                false);
    }

    public SessionStateChange allQuestionsAnsweredPlan(
            InterviewSession session,
            UUID processingToken) {
        requireStatus(session, SessionStatus.IN_PROGRESS);
        verifyClaim(session, SessionProcessingStage.NEXT_TURN, processingToken);
        if ((session.getAwaitingAction() != AwaitingAction.ENGINE_RESPONSE
                && session.getAwaitingAction() != AwaitingAction.ENGINE_RETRY)
                || session.getTotalQuestionCount() == 0
                || session.getAnsweredQuestionCount() != session.getTotalQuestionCount()) {
            throw invalidState();
        }
        return scoringPlan(session, SessionEndReason.USER_COMPLETED, true);
    }

    public SessionStateChange timeoutPlan(InterviewSession session) {
        requireStatus(session, SessionStatus.READY, SessionStatus.IN_PROGRESS, SessionStatus.PAUSED);
        return new SessionStateChange(
                SessionStatus.SCORING,
                AwaitingAction.REPORT,
                SessionEndReason.TIMEOUT_24H,
                null,
                null,
                SessionProcessingStage.SCORING,
                true,
                false);
    }

    public void validateAwaitingAction(SessionStatus status, AwaitingAction awaitingAction) {
        if (!ALLOWED_AWAITING_ACTIONS.get(status).contains(awaitingAction)) {
            throw new IllegalStateException(
                    "Invalid awaiting action for target session status " + status);
        }
    }

    private SessionStateChange nextTurnRetryPlan() {
        return new SessionStateChange(
                SessionStatus.IN_PROGRESS,
                AwaitingAction.ENGINE_RESPONSE,
                null,
                null,
                null,
                SessionProcessingStage.NEXT_TURN,
                true,
                true);
    }

    private SessionStateChange dispatchGenerationPlan(InterviewSession session) {
        requireStatus(session, SessionStatus.CREATED);
        return new SessionStateChange(
                SessionStatus.SCRIPT_GENERATING,
                AwaitingAction.NONE,
                null,
                null,
                null,
                SessionProcessingStage.SCRIPT_GENERATION,
                true,
                false);
    }

    private SessionStateChange startPlan(InterviewSession session) {
        requireStatus(session, SessionStatus.READY);
        if (session.getTotalQuestionCount() < MIN_BASE_QUESTION_COUNT
                || session.getTotalQuestionCount() > MAX_BASE_QUESTION_COUNT) {
            throw invalidState();
        }
        return new SessionStateChange(
                SessionStatus.IN_PROGRESS,
                AwaitingAction.CANDIDATE_ANSWER,
                null,
                null,
                null,
                null,
                false,
                true);
    }

    private SessionStateChange pausePlan(InterviewSession session) {
        requireStatus(session, SessionStatus.IN_PROGRESS);
        if (session.getAwaitingAction() == AwaitingAction.ENGINE_RESPONSE
                || session.getAwaitingAction() == AwaitingAction.ENGINE_RETRY) {
            throw invalidState();
        }
        return new SessionStateChange(
                SessionStatus.PAUSED,
                AwaitingAction.NONE,
                session.getEndReason(),
                null,
                null,
                null,
                false,
                true);
    }

    private SessionStateChange resumePlan(
            InterviewSession session,
            AwaitingAction restoredAwaitingAction) {
        requireStatus(session, SessionStatus.PAUSED);
        if (!IN_PROGRESS_ACTIONS.contains(restoredAwaitingAction)) {
            throw invalidState();
        }
        SessionProcessingStage stage = restoredAwaitingAction == AwaitingAction.ENGINE_RESPONSE
                || restoredAwaitingAction == AwaitingAction.ENGINE_RETRY
                ? SessionProcessingStage.NEXT_TURN
                : null;
        return new SessionStateChange(
                SessionStatus.IN_PROGRESS,
                restoredAwaitingAction,
                session.getEndReason(),
                null,
                null,
                stage,
                false,
                true);
    }

    private SessionStateChange scoringPlan(
            InterviewSession session,
            SessionEndReason endReason,
            boolean updateLastActivity) {
        requireStatus(session, SessionStatus.IN_PROGRESS);
        return new SessionStateChange(
                SessionStatus.SCORING,
                AwaitingAction.REPORT,
                endReason,
                null,
                null,
                SessionProcessingStage.SCORING,
                true,
                updateLastActivity);
    }

    private SessionStateChange reportCommittedPlan(
            InterviewSession session,
            UUID processingToken) {
        requireStatus(session, SessionStatus.SCORING);
        verifyClaim(session, SessionProcessingStage.SCORING, processingToken);
        if (session.getEndReason() == null) {
            throw invalidState();
        }
        return new SessionStateChange(
                SessionStatus.COMPLETED,
                AwaitingAction.NONE,
                session.getEndReason(),
                null,
                null,
                null,
                false,
                false);
    }

    private SessionStateChange failurePlan(
            InterviewSession session,
            UUID processingToken,
            SessionFailureStage failureStage,
            String statusMessage) {
        SessionProcessingStage expectedStage = switch (session.getStatus()) {
            case SCRIPT_GENERATING -> SessionProcessingStage.SCRIPT_GENERATION;
            case IN_PROGRESS -> SessionProcessingStage.NEXT_TURN;
            case SCORING -> SessionProcessingStage.SCORING;
            default -> throw invalidState();
        };
        if (failureStage == null || !failureStage.name().equals(expectedStage.name())) {
            throw invalidState();
        }
        verifyClaim(session, expectedStage, processingToken);
        return new SessionStateChange(
                SessionStatus.FAILED,
                AwaitingAction.ENGINE_RETRY,
                session.getEndReason(),
                failureStage,
                normalizeNullable(statusMessage, STATUS_MESSAGE_MAX_LENGTH),
                null,
                false,
                false);
    }

    private SessionStateChange abandonPlan(InterviewSession session) {
        requireStatus(
                session,
                SessionStatus.READY,
                SessionStatus.IN_PROGRESS,
                SessionStatus.PAUSED,
                SessionStatus.FAILED);
        return new SessionStateChange(
                SessionStatus.ABANDONED,
                AwaitingAction.NONE,
                SessionEndReason.USER_ABANDONED,
                null,
                null,
                null,
                false,
                true);
    }

    private SessionStateChange retryPlan(InterviewSession session) {
        requireStatus(session, SessionStatus.FAILED);
        if (session.getFailureStage() == null) {
            throw invalidState();
        }
        return switch (session.getFailureStage()) {
            case SCRIPT_GENERATION -> new SessionStateChange(
                    SessionStatus.SCRIPT_GENERATING,
                    AwaitingAction.NONE,
                    null,
                    null,
                    null,
                    SessionProcessingStage.SCRIPT_GENERATION,
                    true,
                    true);
            case NEXT_TURN -> new SessionStateChange(
                    SessionStatus.IN_PROGRESS,
                    AwaitingAction.ENGINE_RESPONSE,
                    null,
                    null,
                    null,
                    SessionProcessingStage.NEXT_TURN,
                    true,
                    true);
            case SCORING -> new SessionStateChange(
                    SessionStatus.SCORING,
                    AwaitingAction.REPORT,
                    session.getEndReason(),
                    null,
                    null,
                    SessionProcessingStage.SCORING,
                    true,
                    true);
        };
    }

    private void verifyClaim(
            InterviewSession session,
            SessionProcessingStage expectedStage,
            UUID processingToken) {
        if (processingToken == null
                || session.getProcessingStage() != expectedStage
                || !processingToken.toString().equals(session.getProcessingToken())) {
            throw new IllegalStateException(
                    "Processing claim is no longer owned, sessionId=" + session.getId());
        }
    }

    private void requireStatus(InterviewSession session, SessionStatus... allowedStatuses) {
        for (SessionStatus allowed : allowedStatuses) {
            if (session.getStatus() == allowed) {
                return;
            }
        }
        throw invalidState();
    }

    private String normalizeNullable(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private SessionInvalidStateException invalidState() {
        return new SessionInvalidStateException();
    }

    private static Map<SessionStatus, Set<AwaitingAction>> allowedAwaitingActions() {
        Map<SessionStatus, Set<AwaitingAction>> allowed = new EnumMap<>(SessionStatus.class);
        allowed.put(SessionStatus.CREATED, EnumSet.of(AwaitingAction.NONE));
        allowed.put(SessionStatus.SCRIPT_GENERATING, EnumSet.of(AwaitingAction.NONE));
        allowed.put(SessionStatus.READY, EnumSet.of(AwaitingAction.START_SESSION));
        allowed.put(SessionStatus.IN_PROGRESS, EnumSet.copyOf(IN_PROGRESS_ACTIONS));
        allowed.put(SessionStatus.PAUSED, EnumSet.of(AwaitingAction.NONE));
        allowed.put(SessionStatus.SCORING, EnumSet.of(AwaitingAction.REPORT));
        allowed.put(SessionStatus.FAILED, EnumSet.of(AwaitingAction.ENGINE_RETRY));
        allowed.put(SessionStatus.COMPLETED, EnumSet.of(AwaitingAction.NONE));
        allowed.put(SessionStatus.ABANDONED, EnumSet.of(AwaitingAction.NONE));
        return Map.copyOf(allowed);
    }
}
