package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionContextSnapshot;
import com.baseProject.myBaseProject.entity.SessionStateTransition;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.enums.SessionEndReason;
import com.baseProject.myBaseProject.enums.SessionFailureStage;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.SessionTransitionActor;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.exception.SessionVersionConflictException;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionContextSnapshotRepository;
import com.baseProject.myBaseProject.repository.SessionStateTransitionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SessionStateMachine {

    private static final String MVP_LANGUAGE_CODE = "vi";
    private static final int CREATION_KEY_MAX_LENGTH = 128;
    private static final int REASON_MAX_LENGTH = 255;
    private static final int STATUS_MESSAGE_MAX_LENGTH = 500;
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Set<AwaitingAction> IN_PROGRESS_ACTIONS = EnumSet.of(
            AwaitingAction.CANDIDATE_ANSWER,
            AwaitingAction.TRANSCRIPT_CONFIRMATION,
            AwaitingAction.ENGINE_RESPONSE,
            AwaitingAction.ENGINE_RETRY);
    private static final Map<SessionStatus, Set<AwaitingAction>> ALLOWED_AWAITING_ACTIONS =
            allowedAwaitingActions();

    private final InterviewSessionRepository sessionRepository;
    private final SessionContextSnapshotRepository snapshotRepository;
    private final SessionStateTransitionRepository transitionRepository;
    private final Clock clock;

    @Transactional
    public InterviewSession create(NewInterviewSession command) {
        validateNewSession(command);
        Instant now = clock.instant();
        String creationKey = command.creationKey().strip();

        InterviewSession session = InterviewSession.create(
                command.user(),
                command.profile(),
                command.jobDescription(),
                command.rubricVersion(),
                creationKey,
                command.creationRequestHash(),
                command.difficulty(),
                command.mode(),
                command.languageCode(),
                command.generationSeed(),
                now);
        sessionRepository.save(session);
        snapshotRepository.save(SessionContextSnapshot.create(
                session,
                command.snapshotSchemaVersion(),
                command.profileSnapshot(),
                command.jobDescription().getConfirmedText(),
                now));
        transitionRepository.save(SessionStateTransition.create(
                session,
                null,
                SessionStatus.CREATED,
                SessionTransitionActor.USER,
                null,
                now));
        return session;
    }

    @Transactional
    public InterviewSession transitionUser(
            Long userId,
            Long sessionId,
            long expectedVersion,
            SessionEvent event,
            AwaitingAction restoredAwaitingAction,
            String reason) {
        if (!isUserEvent(event)) {
            throw new IllegalArgumentException("Event is not a user transition: " + event);
        }
        InterviewSession session = sessionRepository
                .findOwnedByIdForUpdate(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        verifyVersion(session, expectedVersion);

        TransitionPlan plan = userPlan(session, event, restoredAwaitingAction);
        return apply(session, plan, SessionTransitionActor.USER, reason);
    }

    @Transactional
    public InterviewSession transitionSystem(
            Long sessionId,
            long expectedVersion,
            SessionEvent event,
            UUID processingToken,
            SessionFailureStage failureStage,
            String statusMessage,
            String reason) {
        if (!isSystemEvent(event)) {
            throw new IllegalArgumentException("Event is not a system transition: " + event);
        }
        InterviewSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(SessionNotFoundException::new);
        verifyVersion(session, expectedVersion);

        TransitionPlan plan = systemPlan(
                session,
                event,
                processingToken,
                failureStage,
                statusMessage);
        return apply(session, plan, SessionTransitionActor.SYSTEM, reason);
    }

    @Transactional
    public InterviewSession timeout(Long sessionId, long expectedVersion, String reason) {
        InterviewSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(SessionNotFoundException::new);
        verifyVersion(session, expectedVersion);
        requireStatus(session, SessionStatus.READY, SessionStatus.IN_PROGRESS, SessionStatus.PAUSED);

        TransitionPlan plan = new TransitionPlan(
                SessionStatus.SCORING,
                AwaitingAction.REPORT,
                SessionEndReason.TIMEOUT_24H,
                null,
                null,
                SessionProcessingStage.SCORING,
                true,
                false);
        return apply(session, plan, SessionTransitionActor.SCHEDULER, reason);
    }

    private InterviewSession apply(
            InterviewSession session,
            TransitionPlan plan,
            SessionTransitionActor actor,
            String reason) {
        validateAwaitingAction(plan.targetStatus(), plan.awaitingAction());
        SessionStatus previousStatus = session.getStatus();
        Instant now = clock.instant();
        session.applyStateTransition(
                plan.targetStatus(),
                plan.awaitingAction(),
                plan.endReason(),
                plan.failureStage(),
                plan.statusMessage(),
                plan.processingStage(),
                plan.resetProcessingAttempts(),
                plan.updateLastActivity(),
                now);
        transitionRepository.save(SessionStateTransition.create(
                session,
                previousStatus,
                plan.targetStatus(),
                actor,
                normalizeNullable(reason, REASON_MAX_LENGTH),
                now));
        return sessionRepository.saveAndFlush(session);
    }

    private TransitionPlan userPlan(
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

    private TransitionPlan systemPlan(
            InterviewSession session,
            SessionEvent event,
            UUID processingToken,
            SessionFailureStage failureStage,
            String statusMessage) {
        return switch (event) {
            case DISPATCH_SCRIPT_GENERATION -> dispatchGenerationPlan(session);
            case SCRIPT_PERSISTED -> scriptPersistedPlan(session, processingToken);
            case ALL_QUESTIONS_ANSWERED -> allQuestionsAnsweredPlan(session);
            case REPORT_COMMITTED -> reportCommittedPlan(session, processingToken);
            case WORKFLOW_FAILED -> failurePlan(
                    session, processingToken, failureStage, statusMessage);
            default -> throw invalidState();
        };
    }

    private TransitionPlan dispatchGenerationPlan(InterviewSession session) {
        requireStatus(session, SessionStatus.CREATED);
        return new TransitionPlan(
                SessionStatus.SCRIPT_GENERATING,
                AwaitingAction.NONE,
                null,
                null,
                null,
                SessionProcessingStage.SCRIPT_GENERATION,
                true,
                false);
    }

    private TransitionPlan scriptPersistedPlan(
            InterviewSession session,
            UUID processingToken) {
        requireStatus(session, SessionStatus.SCRIPT_GENERATING);
        verifyClaim(session, SessionProcessingStage.SCRIPT_GENERATION, processingToken);
        if (session.getTotalQuestionCount() < 5 || session.getTotalQuestionCount() > 7) {
            throw invalidState();
        }
        return new TransitionPlan(
                SessionStatus.READY,
                AwaitingAction.START_SESSION,
                null,
                null,
                null,
                null,
                false,
                false);
    }

    private TransitionPlan startPlan(InterviewSession session) {
        requireStatus(session, SessionStatus.READY);
        if (session.getTotalQuestionCount() < 5 || session.getTotalQuestionCount() > 7) {
            throw invalidState();
        }
        return new TransitionPlan(
                SessionStatus.IN_PROGRESS,
                AwaitingAction.CANDIDATE_ANSWER,
                null,
                null,
                null,
                null,
                false,
                true);
    }

    private TransitionPlan pausePlan(InterviewSession session) {
        requireStatus(session, SessionStatus.IN_PROGRESS);
        if (session.getAwaitingAction() == AwaitingAction.ENGINE_RESPONSE
                || session.getAwaitingAction() == AwaitingAction.ENGINE_RETRY) {
            throw invalidState();
        }
        return new TransitionPlan(
                SessionStatus.PAUSED,
                AwaitingAction.NONE,
                session.getEndReason(),
                null,
                null,
                null,
                false,
                true);
    }

    private TransitionPlan resumePlan(
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
        return new TransitionPlan(
                SessionStatus.IN_PROGRESS,
                restoredAwaitingAction,
                session.getEndReason(),
                null,
                null,
                stage,
                false,
                true);
    }

    private TransitionPlan allQuestionsAnsweredPlan(InterviewSession session) {
        requireStatus(session, SessionStatus.IN_PROGRESS);
        if (session.getTotalQuestionCount() == 0
                || session.getAnsweredQuestionCount() != session.getTotalQuestionCount()) {
            throw invalidState();
        }
        return scoringPlan(session, SessionEndReason.USER_COMPLETED, true);
    }

    private TransitionPlan scoringPlan(
            InterviewSession session,
            SessionEndReason endReason,
            boolean updateLastActivity) {
        requireStatus(session, SessionStatus.IN_PROGRESS);
        return new TransitionPlan(
                SessionStatus.SCORING,
                AwaitingAction.REPORT,
                endReason,
                null,
                null,
                SessionProcessingStage.SCORING,
                true,
                updateLastActivity);
    }

    private TransitionPlan reportCommittedPlan(
            InterviewSession session,
            UUID processingToken) {
        requireStatus(session, SessionStatus.SCORING);
        verifyClaim(session, SessionProcessingStage.SCORING, processingToken);
        if (session.getEndReason() == null) {
            throw invalidState();
        }
        return new TransitionPlan(
                SessionStatus.COMPLETED,
                AwaitingAction.NONE,
                session.getEndReason(),
                null,
                null,
                null,
                false,
                false);
    }

    private TransitionPlan failurePlan(
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
        return new TransitionPlan(
                SessionStatus.FAILED,
                AwaitingAction.ENGINE_RETRY,
                session.getEndReason(),
                failureStage,
                normalizeNullable(statusMessage, STATUS_MESSAGE_MAX_LENGTH),
                null,
                false,
                false);
    }

    private TransitionPlan abandonPlan(InterviewSession session) {
        requireStatus(
                session,
                SessionStatus.READY,
                SessionStatus.IN_PROGRESS,
                SessionStatus.PAUSED,
                SessionStatus.FAILED);
        return new TransitionPlan(
                SessionStatus.ABANDONED,
                AwaitingAction.NONE,
                SessionEndReason.USER_ABANDONED,
                null,
                null,
                null,
                false,
                true);
    }

    private TransitionPlan retryPlan(InterviewSession session) {
        requireStatus(session, SessionStatus.FAILED);
        if (session.getFailureStage() == null) {
            throw invalidState();
        }
        return switch (session.getFailureStage()) {
            case SCRIPT_GENERATION -> new TransitionPlan(
                    SessionStatus.SCRIPT_GENERATING,
                    AwaitingAction.NONE,
                    null,
                    null,
                    null,
                    SessionProcessingStage.SCRIPT_GENERATION,
                    true,
                    true);
            case NEXT_TURN -> new TransitionPlan(
                    SessionStatus.IN_PROGRESS,
                    AwaitingAction.ENGINE_RESPONSE,
                    null,
                    null,
                    null,
                    SessionProcessingStage.NEXT_TURN,
                    true,
                    true);
            case SCORING -> new TransitionPlan(
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

    private void validateNewSession(NewInterviewSession command) {
        Objects.requireNonNull(command);
        Objects.requireNonNull(command.user());
        Objects.requireNonNull(command.profile());
        Objects.requireNonNull(command.jobDescription());
        Objects.requireNonNull(command.rubricVersion());
        Objects.requireNonNull(command.difficulty());
        Objects.requireNonNull(command.mode());
        Objects.requireNonNull(command.generationSeed());
        if (command.user().getId() == null
                || !Objects.equals(command.profile().getUser().getId(), command.user().getId())
                || !Objects.equals(command.jobDescription().getUser().getId(), command.user().getId())) {
            throw new IllegalArgumentException("Session inputs must belong to the same persisted user");
        }
        if (!command.profile().isConfirmed()
                || !command.jobDescription().isActive()
                || command.jobDescription().getStatus() != JobDescriptionStatus.READY
                || command.rubricVersion().getPublishedAt() == null) {
            throw new IllegalArgumentException("Session inputs are not ready");
        }
        if (command.creationKey() == null
                || command.creationKey().isBlank()
                || command.creationKey().strip().length() > CREATION_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("Creation key must contain 1-128 characters");
        }
        if (command.creationRequestHash() == null
                || !SHA_256.matcher(command.creationRequestHash()).matches()) {
            throw new IllegalArgumentException("Creation request hash must be lowercase SHA-256");
        }
        if (!MVP_LANGUAGE_CODE.equals(command.languageCode())) {
            throw new IllegalArgumentException("Only language code vi is supported in the MVP");
        }
    }

    private void verifyVersion(InterviewSession session, long expectedVersion) {
        if (expectedVersion < 0 || session.getVersion() != expectedVersion) {
            throw new SessionVersionConflictException();
        }
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

    private void validateAwaitingAction(SessionStatus status, AwaitingAction awaitingAction) {
        if (!ALLOWED_AWAITING_ACTIONS.get(status).contains(awaitingAction)) {
            throw new IllegalStateException(
                    "Invalid awaiting action for target session status " + status);
        }
    }

    private boolean isUserEvent(SessionEvent event) {
        return event == SessionEvent.START
                || event == SessionEvent.PAUSE
                || event == SessionEvent.RESUME
                || event == SessionEvent.COMPLETE_EARLY
                || event == SessionEvent.ABANDON
                || event == SessionEvent.RETRY;
    }

    private boolean isSystemEvent(SessionEvent event) {
        return event == SessionEvent.DISPATCH_SCRIPT_GENERATION
                || event == SessionEvent.SCRIPT_PERSISTED
                || event == SessionEvent.ALL_QUESTIONS_ANSWERED
                || event == SessionEvent.REPORT_COMMITTED
                || event == SessionEvent.WORKFLOW_FAILED;
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

    private record TransitionPlan(
            SessionStatus targetStatus,
            AwaitingAction awaitingAction,
            SessionEndReason endReason,
            SessionFailureStage failureStage,
            String statusMessage,
            SessionProcessingStage processingStage,
            boolean resetProcessingAttempts,
            boolean updateLastActivity) {
    }
}
