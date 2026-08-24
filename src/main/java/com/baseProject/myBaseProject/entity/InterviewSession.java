package com.baseProject.myBaseProject.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.baseProject.myBaseProject.enums.InterviewMode;
import com.baseProject.myBaseProject.enums.SessionEndReason;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One mock interview run. The profile and rubric version are pinned at creation so a report
 * can be re-rendered later even after the user edits their CV or the rubric is revised.
 */
@Entity
@Table(
        name = "interview_sessions",
        indexes = {
                @Index(name = "idx_interview_sessions_user_status", columnList = "user_id, status"),
                @Index(name = "idx_interview_sessions_user_completed", columnList = "user_id, completed_at"),
                @Index(name = "idx_interview_sessions_last_activity", columnList = "last_activity_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private CandidateProfile profile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rubric_version_id", nullable = false)
    private RubricVersion rubricVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    /** Index of the turn to be played next; used to resume a session on a new connection. */
    @Column(name = "current_turn_index", nullable = false)
    @Builder.Default
    private int currentTurnIndex = 0;

    /** Seed the question script was generated with, so the same script can be reproduced. */
    @Column(name = "script_seed", length = 64)
    private String scriptSeed;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private SessionEndReason endReason;

    @Column(name = "started_at")
    private Instant startedAt;

    /** Touched on every turn. The 24h-timeout job reads this column.  */
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Copy of session_reports.overall_score, kept here so a history list needs no join. */
    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
