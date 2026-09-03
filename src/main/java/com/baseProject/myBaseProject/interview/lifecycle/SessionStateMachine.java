package com.baseProject.myBaseProject.interview.lifecycle;

import static com.baseProject.myBaseProject.constant.InterviewConstraints.MAX_BASE_QUESTION_COUNT;
import static com.baseProject.myBaseProject.constant.InterviewConstraints.MIN_BASE_QUESTION_COUNT;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionStateTransition;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionEndReason;
import com.baseProject.myBaseProject.enums.SessionFailureStage;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.SessionTransitionActor;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.exception.SessionRetryNotAllowedException;
import com.baseProject.myBaseProject.exception.SessionVersionConflictException;
import com.baseProject.myBaseProject.interview.lifecycle.model.NewInterviewSession;
import com.baseProject.myBaseProject.interview.snapshot.SessionContextSnapshotFactory;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionContextSnapshotRepository;
import com.baseProject.myBaseProject.repository.SessionStateTransitionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionStateMachine {

    private static final int REASON_MAX_LENGTH = 255;
    private static final int STATUS_MESSAGE_MAX_LENGTH = 500;
    private static final Set<AwaitingAction> RESUMABLE_ACTIONS = EnumSet.of(
            AwaitingAction.CANDIDATE_ANSWER,
            AwaitingAction.TRANSCRIPT_CONFIRMATION);

    private final InterviewSessionRepository sessionRepository;
    private final SessionContextSnapshotRepository snapshotRepository;
    private final SessionStateTransitionRepository transitionRepository;
    private final SessionContextSnapshotFactory snapshotFactory;
    private final Clock clock;

    @Transactional
    public InterviewSession create(NewInterviewSession command) {
        Objects.requireNonNull(command);
        Instant now = clock.instant();
        InterviewSession session = InterviewSession.create(
                command.user(),
                command.profile(),
                command.jobDescription(),
                command.rubricVersion(),
                command.creationKey().strip(),
                command.creationRequestHash(),
                command.difficulty(),
                command.mode(),
                command.languageCode(),
                command.generationSeed(),
                now);
        sessionRepository.save(session);
        snapshotRepository.save(snapshotFactory.create(
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
    public InterviewSession dispatchScriptGeneration(
            Long sessionId,
            long expectedVersion,
            String reason) {
        InterviewSession session = findSystemSession(sessionId, expectedVersion);
        requireStatus(session, SessionStatus.CREATED);

        return apply(
                session,
                new Transition(
                        SessionStatus.SCRIPT_GENERATING,
                        AwaitingAction.NONE,
                        null,
                        null,
                        null,
                        SessionProcessingStage.SCRIPT_GENERATION,
                        true,
                        false),
                SessionTransitionActor.SYSTEM,
                reason);
    }

    @Transactional
    public InterviewSession start(
            Long userId,
            Long sessionId,
            long expectedVersion,
            String reason) {
        InterviewSession session = findOwnedSession(userId, sessionId, expectedVersion);
        requireStatus(session, SessionStatus.READY);
        requireQuestionCount(session);
        return apply(
                session,
                new Transition(
                        SessionStatus.IN_PROGRESS,
                        AwaitingAction.CANDIDATE_ANSWER,
                        null,
                        null,
                        null,
                        null,
                        false,
                        true),
                SessionTransitionActor.USER,
                reason);
    }

    @Transactional
    public InterviewSession pause(
            Long userId,
            Long sessionId,
            long expectedVersion,
            String reason) {
        InterviewSession session = findOwnedSession(userId, sessionId, expectedVersion);
        requireStatus(session, SessionStatus.IN_PROGRESS);
        if (session.getAwaitingAction() != AwaitingAction.CANDIDATE_ANSWER
                && session.getAwaitingAction() != AwaitingAction.TRANSCRIPT_CONFIRMATION) {
            throw invalidState();
        }
        return apply(
                session,
                new Transition(
                        SessionStatus.PAUSED,
                        AwaitingAction.NONE,
                        session.getEndReason(),
                        null,
                        null,
                        null,
                        false,
                        true),
                SessionTransitionActor.USER,
                reason);
    }

    @Transactional
    public InterviewSession resume(
            Long userId,
            Long sessionId,
            long expectedVersion,
            AwaitingAction restoredAction,
            String reason) {
        InterviewSession session = findOwnedSession(userId, sessionId, expectedVersion);
        requireStatus(session, SessionStatus.PAUSED);
        if (!RESUMABLE_ACTIONS.contains(restoredAction)) {
            throw invalidState();
        }
        return apply(
                session,
                new Transition(
                        SessionStatus.IN_PROGRESS,
                        restoredAction,
                        session.getEndReason(),
                        null,
                        null,
                        null,
                        false,
                        true),
                SessionTransitionActor.USER,
                reason);
    }

    @Transactional
    public InterviewSession retryUserWorkflow(
            Long userId,
            Long sessionId,
            long expectedVersion,
            Set<SessionFailureStage> allowedFailureStages,
            String reason) {
        InterviewSession session = findOwnedSession(userId, sessionId, expectedVersion);
        return apply(
                session,
                retryTransition(session, allowedFailureStages),
                SessionTransitionActor.USER,
                reason);
    }

    @Transactional
    public InterviewSession completeScriptGeneration(
            InterviewSession lockedSession,
            UUID processingToken,
            String reason) {
        requirePersisted(lockedSession);
        requireStatus(lockedSession, SessionStatus.SCRIPT_GENERATING);
        verifyClaim(lockedSession, SessionProcessingStage.SCRIPT_GENERATION, processingToken);
        requireQuestionCount(lockedSession);
        return apply(
                lockedSession,
                new Transition(
                        SessionStatus.READY,
                        AwaitingAction.START_SESSION,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false),
                SessionTransitionActor.SYSTEM,
                reason);
    }

    @Transactional
    public InterviewSession completeAllQuestions(
            InterviewSession lockedSession,
            UUID processingToken,
            String reason) {
        requirePersisted(lockedSession);
        requireStatus(lockedSession, SessionStatus.IN_PROGRESS);
        verifyClaim(lockedSession, SessionProcessingStage.NEXT_TURN, processingToken);
        if ((lockedSession.getAwaitingAction() != AwaitingAction.ENGINE_RESPONSE
                && lockedSession.getAwaitingAction() != AwaitingAction.ENGINE_RETRY)
                || lockedSession.getTotalQuestionCount() == 0
                || lockedSession.getAnsweredQuestionCount()
                        != lockedSession.getTotalQuestionCount()) {
            throw invalidState();
        }
        return apply(
                lockedSession,
                new Transition(
                        SessionStatus.SCORING,
                        AwaitingAction.REPORT,
                        SessionEndReason.USER_COMPLETED,
                        null,
                        null,
                        SessionProcessingStage.SCORING,
                        true,
                        true),
                SessionTransitionActor.SYSTEM,
                reason);
    }

    @Transactional
    public InterviewSession failWorkflow(
            Long sessionId,
            long expectedVersion,
            UUID processingToken,
            SessionFailureStage failureStage,
            String statusMessage,
            String reason) {
        InterviewSession session = findSystemSession(sessionId, expectedVersion);
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
        return apply(
                session,
                new Transition(
                        SessionStatus.FAILED,
                        AwaitingAction.ENGINE_RETRY,
                        session.getEndReason(),
                        failureStage,
                        normalizeNullable(statusMessage, STATUS_MESSAGE_MAX_LENGTH),
                        null,
                        false,
                        false),
                SessionTransitionActor.SYSTEM,
                reason);
    }

    private InterviewSession findOwnedSession(
            Long userId,
            Long sessionId,
            long expectedVersion) {
        InterviewSession session = sessionRepository
                .findOwnedByIdForUpdate(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        verifyVersion(session, expectedVersion);
        return session;
    }

    private InterviewSession findSystemSession(Long sessionId, long expectedVersion) {
        InterviewSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(SessionNotFoundException::new);
        verifyVersion(session, expectedVersion);
        return session;
    }

    private Transition retryTransition(
            InterviewSession session,
            Set<SessionFailureStage> allowedFailureStages) {
        if (session.getStatus() == SessionStatus.IN_PROGRESS
                && session.getAwaitingAction() == AwaitingAction.ENGINE_RETRY
                && session.getProcessingStage() == SessionProcessingStage.NEXT_TURN
                && session.getProcessingToken() == null) {
            return retryTransition(
                    SessionStatus.IN_PROGRESS,
                    AwaitingAction.ENGINE_RESPONSE,
                    null,
                    SessionProcessingStage.NEXT_TURN);
        }
        if (session.getStatus() != SessionStatus.FAILED
                || session.getFailureStage() == null
                || allowedFailureStages == null
                || !allowedFailureStages.contains(session.getFailureStage())) {
            throw new SessionRetryNotAllowedException();
        }
        return switch (session.getFailureStage()) {
            case SCRIPT_GENERATION -> retryTransition(
                    SessionStatus.SCRIPT_GENERATING,
                    AwaitingAction.NONE,
                    null,
                    SessionProcessingStage.SCRIPT_GENERATION);
            case NEXT_TURN -> retryTransition(
                    SessionStatus.IN_PROGRESS,
                    AwaitingAction.ENGINE_RESPONSE,
                    null,
                    SessionProcessingStage.NEXT_TURN);
            case SCORING -> retryTransition(
                    SessionStatus.SCORING,
                    AwaitingAction.REPORT,
                    session.getEndReason(),
                    SessionProcessingStage.SCORING);
        };
    }

    private Transition retryTransition(
            SessionStatus status,
            AwaitingAction awaitingAction,
            SessionEndReason endReason,
            SessionProcessingStage processingStage) {
        return new Transition(
                status,
                awaitingAction,
                endReason,
                null,
                null,
                processingStage,
                true,
                true);
    }

    private InterviewSession apply(
            InterviewSession session,
            Transition transition,
            SessionTransitionActor actor,
            String reason) {
        SessionStatus previousStatus = session.getStatus();
        Instant now = clock.instant();

        session.applyStateTransition(
                transition.status(),
                transition.awaitingAction(),
                transition.endReason(),
                transition.failureStage(),
                transition.statusMessage(),
                transition.processingStage(),
                transition.resetProcessingAttempts(),
                transition.updateLastActivity(),
                now);

        transitionRepository.save(SessionStateTransition.create(
                session,
                previousStatus,
                transition.status(),
                actor,
                normalizeNullable(reason, REASON_MAX_LENGTH),
                now));

        return sessionRepository.saveAndFlush(session);
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

    private void requireQuestionCount(InterviewSession session) {
        if (session.getTotalQuestionCount() < MIN_BASE_QUESTION_COUNT
                || session.getTotalQuestionCount() > MAX_BASE_QUESTION_COUNT) {
            throw invalidState();
        }
    }

    private void requirePersisted(InterviewSession session) {
        Objects.requireNonNull(session);
        if (session.getId() == null) {
            throw new IllegalArgumentException("Session must be persisted");
        }
    }

    private void requireStatus(InterviewSession session, SessionStatus expectedStatus) {
        if (session.getStatus() != expectedStatus) {
            throw invalidState();
        }
    }

    private void verifyVersion(InterviewSession session, long expectedVersion) {
        if (expectedVersion < 0 || session.getVersion() != expectedVersion) {
            throw new SessionVersionConflictException();
        }
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

    private record Transition(
            SessionStatus status,
            AwaitingAction awaitingAction,
            SessionEndReason endReason,
            SessionFailureStage failureStage,
            String statusMessage,
            SessionProcessingStage processingStage,
            boolean resetProcessingAttempts,
            boolean updateLastActivity) {
    }
}
