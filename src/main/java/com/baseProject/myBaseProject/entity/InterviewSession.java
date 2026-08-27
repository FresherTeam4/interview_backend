package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.InterviewDifficulty;
import com.baseProject.myBaseProject.enums.SessionEndReason;
import com.baseProject.myBaseProject.enums.SessionFailureStage;
import com.baseProject.myBaseProject.enums.SessionMode;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "interview_sessions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_interview_sessions_user_creation_key",
                        columnNames = {"user_id", "creation_key"})
        },
        indexes = {
                @Index(
                        name = "idx_interview_sessions_user_status_activity",
                        columnList = "user_id, status, last_activity_at"),
                @Index(
                        name = "idx_interview_sessions_user_completed",
                        columnList = "user_id, completed_at"),
                @Index(name = "idx_interview_sessions_profile_id", columnList = "profile_id"),
                @Index(
                        name = "idx_interview_sessions_job_description_id",
                        columnList = "job_description_id"),
                @Index(
                        name = "idx_interview_sessions_rubric_version_id",
                        columnList = "rubric_version_id"),
                @Index(
                        name = "idx_interview_sessions_recovery",
                        columnList = "status, processing_stage, next_retry_at"),
                @Index(
                        name = "idx_interview_sessions_last_activity",
                        columnList = "last_activity_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, updatable = false)
    private CandidateProfile profile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_description_id", nullable = false, updatable = false)
    private JobDescription jobDescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rubric_version_id", nullable = false, updatable = false)
    private RubricVersion rubricVersion;

    @Column(name = "creation_key", nullable = false, length = 128, updatable = false)
    private String creationKey;

    @Column(
            name = "creation_request_hash",
            nullable = false,
            length = 64,
            updatable = false,
            columnDefinition = "CHAR(64)")
    private String creationRequestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private InterviewDifficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private SessionMode mode;

    @Column(name = "language_code", nullable = false, length = 10, updatable = false)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "awaiting_action", nullable = false, length = 40)
    private AwaitingAction awaitingAction;

    @Column(name = "current_question_ordinal")
    private Short currentQuestionOrdinal;

    @Column(name = "next_turn_index", nullable = false)
    private int nextTurnIndex;

    @Column(name = "current_followup_depth", nullable = false)
    private short currentFollowupDepth;

    @Column(name = "total_followup_count", nullable = false)
    private short totalFollowupCount;

    @Column(name = "answered_question_count", nullable = false)
    private short answeredQuestionCount;

    @Column(name = "total_question_count", nullable = false)
    private short totalQuestionCount;

    @Column(
            name = "generation_seed",
            nullable = false,
            length = 36,
            updatable = false,
            columnDefinition = "CHAR(36)")
    private String generationSeed;

    @Version
    @Column(nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_stage", length = 30)
    private SessionProcessingStage processingStage;

    @Column(name = "processing_token", length = 36, columnDefinition = "CHAR(36)")
    private String processingToken;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "processing_attempts", nullable = false)
    private short processingAttempts;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_stage", length = 30)
    private SessionFailureStage failureStage;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 40)
    private SessionEndReason endReason;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static InterviewSession create(
            UserAccount user,
            CandidateProfile profile,
            JobDescription jobDescription,
            RubricVersion rubricVersion,
            String creationKey,
            String creationRequestHash,
            InterviewDifficulty difficulty,
            SessionMode mode,
            String languageCode,
            UUID generationSeed,
            Instant now) {
        InterviewSession session = new InterviewSession();
        session.user = Objects.requireNonNull(user);
        session.profile = Objects.requireNonNull(profile);
        session.jobDescription = Objects.requireNonNull(jobDescription);
        session.rubricVersion = Objects.requireNonNull(rubricVersion);
        session.creationKey = Objects.requireNonNull(creationKey);
        session.creationRequestHash = Objects.requireNonNull(creationRequestHash);
        session.difficulty = Objects.requireNonNull(difficulty);
        session.mode = Objects.requireNonNull(mode);
        session.languageCode = Objects.requireNonNull(languageCode);
        session.status = SessionStatus.CREATED;
        session.awaitingAction = AwaitingAction.NONE;
        session.generationSeed = Objects.requireNonNull(generationSeed).toString();
        session.lastActivityAt = Objects.requireNonNull(now);
        session.createdAt = now;
        session.updatedAt = now;
        return session;
    }

    /** Records the all-or-nothing base script size immediately before the READY transition. */
    public void recordGeneratedQuestionCount(int questionCount) {
        if (status != SessionStatus.SCRIPT_GENERATING
                || totalQuestionCount != 0
                || questionCount < 5
                || questionCount > 7) {
            throw new IllegalStateException("Generated question count cannot be recorded");
        }
        totalQuestionCount = (short) questionCount;
    }

    /** Initializes the persisted conversation cursor together with the first interviewer turn. */
    public int beginAtQuestion(short questionOrdinal) {
        if (status != SessionStatus.IN_PROGRESS
                || awaitingAction != AwaitingAction.CANDIDATE_ANSWER
                || currentQuestionOrdinal != null
                || nextTurnIndex != 0
                || startedAt == null
                || questionOrdinal != 1
                || questionOrdinal > totalQuestionCount) {
            throw new IllegalStateException("Interview session cannot initialize its first prompt");
        }
        currentQuestionOrdinal = questionOrdinal;
        return nextTurnIndex++;
    }

    /** State mutation entry point; production callers go through SessionStateMachine. */
    public void applyStateTransition(
            SessionStatus targetStatus,
            AwaitingAction targetAwaitingAction,
            SessionEndReason targetEndReason,
            SessionFailureStage targetFailureStage,
            String targetStatusMessage,
            SessionProcessingStage targetProcessingStage,
            boolean resetProcessingAttempts,
            boolean updateLastActivity,
            Instant now) {
        SessionStatus previousStatus = status;
        status = Objects.requireNonNull(targetStatus);
        awaitingAction = Objects.requireNonNull(targetAwaitingAction);
        endReason = targetEndReason;
        failureStage = targetFailureStage;
        statusMessage = targetStatusMessage;
        processingStage = targetProcessingStage;
        processingToken = null;
        processingStartedAt = null;
        nextRetryAt = null;
        if (resetProcessingAttempts) {
            processingAttempts = 0;
        }
        if (previousStatus == SessionStatus.READY && targetStatus == SessionStatus.IN_PROGRESS) {
            startedAt = now;
        }
        if (targetStatus.isTerminal()) {
            completedAt = now;
        } else {
            completedAt = null;
        }
        if (updateLastActivity) {
            lastActivityAt = now;
        }
        updatedAt = now;
    }
}
