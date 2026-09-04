package com.baseProject.myBaseProject.interview.lifecycle;

import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.interview.scoring.InterviewScoringStore;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowDispatcher;
import com.baseProject.myBaseProject.interview.workflow.SessionProcessingClaimService;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewSessionExpiryService {

    private static final String TIMEOUT_REASON = "Interview expired after inactivity";
    private static final Set<SessionStatus> EXPIRABLE_STATUSES = EnumSet.of(
            SessionStatus.READY,
            SessionStatus.IN_PROGRESS,
            SessionStatus.PAUSED);

    private final InterviewProperties properties;
    private final InterviewSessionRepository sessionRepository;
    private final SessionStateMachine stateMachine;
    private final InterviewScoringStore scoringStore;
    private final SessionProcessingClaimService claimService;
    private final InterviewWorkflowDispatcher workflowDispatcher;

    @Transactional
    public ExpiryOutcome expireIfInactive(Long sessionId, Instant inactivityCutoff) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(inactivityCutoff);
        InterviewSession session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (session == null
                || !EXPIRABLE_STATUSES.contains(session.getStatus())
                || session.getLastActivityAt().isAfter(inactivityCutoff)) {
            return ExpiryOutcome.SKIPPED;
        }

        InterviewSession scoring = stateMachine.timeout(
                session,
                inactivityCutoff,
                TIMEOUT_REASON);
        if (scoring.getAnsweredQuestionCount() == 0) {
            scoringStore.commitInsufficientEvidence(scoring);
            return ExpiryOutcome.COMPLETED_WITHOUT_EVIDENCE;
        }

        UUID processingToken = UUID.randomUUID();
        boolean claimed = claimService.claim(
                sessionId,
                SessionProcessingStage.SCORING,
                processingToken,
                properties.processingLease());
        if (!claimed) {
            throw new IllegalStateException(
                    "Timed-out scoring work could not be claimed, sessionId=" + sessionId);
        }
        workflowDispatcher.dispatchAfterCommit(
                sessionId,
                SessionProcessingStage.SCORING,
                processingToken);
        return ExpiryOutcome.SCORING_DISPATCHED;
    }

    public enum ExpiryOutcome {
        SKIPPED,
        COMPLETED_WITHOUT_EVIDENCE,
        SCORING_DISPATCHED
    }
}
