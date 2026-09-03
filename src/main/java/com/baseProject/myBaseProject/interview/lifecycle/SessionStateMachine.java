package com.baseProject.myBaseProject.interview.lifecycle;

import com.baseProject.myBaseProject.interview.lifecycle.model.NewInterviewSession;
import com.baseProject.myBaseProject.interview.lifecycle.model.SessionEvent;
import com.baseProject.myBaseProject.interview.lifecycle.model.SessionStateChange;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionStateTransition;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionFailureStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.SessionTransitionActor;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionStateMachine {

    private static final int REASON_MAX_LENGTH = 255;

    private final InterviewSessionFactory sessionFactory;
    private final SessionTransitionPlanner transitionPlanner;
    private final InterviewSessionRepository sessionRepository;
    private final SessionContextSnapshotRepository snapshotRepository;
    private final SessionStateTransitionRepository transitionRepository;
    private final Clock clock;

    @Transactional
    public InterviewSession create(NewInterviewSession command) {
        sessionFactory.validate(command);
        Instant now = clock.instant();

        InterviewSession session = sessionFactory.createSession(command, now);
        sessionRepository.save(session);
        snapshotRepository.save(sessionFactory.createSnapshot(session, command, now));
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
        if (!transitionPlanner.isUserEvent(event)) {
            throw new IllegalArgumentException("Event is not a user transition: " + event);
        }
        InterviewSession session = sessionRepository
                .findOwnedByIdForUpdate(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        verifyVersion(session, expectedVersion);

        SessionStateChange plan = transitionPlanner.userPlan(session, event, restoredAwaitingAction);
        return apply(session, plan, SessionTransitionActor.USER, reason);
    }

    @Transactional
    public InterviewSession retryUserWorkflow(
            Long userId,
            Long sessionId,
            long expectedVersion,
            Set<SessionFailureStage> allowedFailureStages,
            String reason) {
        InterviewSession session = sessionRepository
                .findOwnedByIdForUpdate(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        verifyVersion(session, expectedVersion);

        SessionStateChange plan = transitionPlanner.retryUserPlan(session, allowedFailureStages);
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
        if (!transitionPlanner.isSystemEvent(event)) {
            throw new IllegalArgumentException("Event is not a system transition: " + event);
        }
        InterviewSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(SessionNotFoundException::new);
        verifyVersion(session, expectedVersion);

        SessionStateChange plan = transitionPlanner.systemPlan(
                session,
                event,
                processingToken,
                failureStage,
                statusMessage);
        return apply(session, plan, SessionTransitionActor.SYSTEM, reason);
    }

    /**
     * Completes script persistence for a session already protected by the caller's pessimistic
     * lock. Avoiding a second lock query is important here: the query would auto-flush the newly
     * recorded question count and advance {@code @Version} before the transition is applied.
     */
    @Transactional
    public InterviewSession completeScriptGeneration(
            InterviewSession lockedSession,
            UUID processingToken,
            String reason) {
        Objects.requireNonNull(lockedSession);
        if (lockedSession.getId() == null) {
            throw new IllegalArgumentException("Script generation session must be persisted");
        }
        SessionStateChange plan = transitionPlanner.scriptPersistedPlan(lockedSession, processingToken);
        return apply(lockedSession, plan, SessionTransitionActor.SYSTEM, reason);
    }

    /** Completes base-question progression without re-locking the caller's managed session. */
    @Transactional
    public InterviewSession completeAllQuestions(
            InterviewSession lockedSession,
            UUID processingToken,
            String reason) {
        Objects.requireNonNull(lockedSession);
        if (lockedSession.getId() == null) {
            throw new IllegalArgumentException("Next-turn session must be persisted");
        }
        SessionStateChange plan = transitionPlanner.allQuestionsAnsweredPlan(lockedSession, processingToken);
        return apply(lockedSession, plan, SessionTransitionActor.SYSTEM, reason);
    }

    @Transactional
    public InterviewSession timeout(Long sessionId, long expectedVersion, String reason) {
        InterviewSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(SessionNotFoundException::new);
        verifyVersion(session, expectedVersion);

        SessionStateChange plan = transitionPlanner.timeoutPlan(session);
        return apply(session, plan, SessionTransitionActor.SCHEDULER, reason);
    }

    private InterviewSession apply(
            InterviewSession session,
            SessionStateChange plan,
            SessionTransitionActor actor,
            String reason) {
        transitionPlanner.validateAwaitingAction(plan.targetStatus(), plan.awaitingAction());
        SessionStatus previousStatus = session.getStatus();
        Instant now = clock.instant();
        session.applyStateTransition(plan, now);
        transitionRepository.save(SessionStateTransition.create(
                session,
                previousStatus,
                plan.targetStatus(),
                actor,
                normalizeNullable(reason, REASON_MAX_LENGTH),
                now));
        return sessionRepository.saveAndFlush(session);
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
}
