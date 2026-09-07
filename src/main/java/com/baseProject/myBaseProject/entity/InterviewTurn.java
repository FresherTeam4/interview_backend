package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.CandidateIntent;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnProcessingStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "interview_turns",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_interview_turn_index",
                        columnNames = {"session_id", "turn_index"}),
                @UniqueConstraint(
                        name = "uq_interview_turn_idempotency",
                        columnNames = {"session_id", "idempotency_key"}),
                @UniqueConstraint(
                        name = "uq_interview_turn_reply_to",
                        columnNames = "reply_to_turn_id")
        },
        indexes = @Index(
                name = "idx_interview_turn_session_created",
                columnList = "session_id, created_at"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewTurn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_turn_id", updatable = false)
    private InterviewTurn replyToTurn;

    @Column(name = "turn_index", nullable = false, updatable = false)
    private int turnIndex;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 20)
    private InterviewTurnRole role;

    @Column(name = "content_text", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String contentText;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "candidate_intent", length = 40)
    private CandidateIntent candidateIntent;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20, updatable = false)
    private InterviewTurnAction action;

    @Column(name = "focus_area_code", length = 50, updatable = false)
    private String focusAreaCode;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "processing_status", length = 20)
    private InterviewTurnProcessingStatus processingStatus;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "processing_error_code", length = 80)
    private String processingErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void retryProcessing(Instant now) {
        processingStatus = InterviewTurnProcessingStatus.PROCESSING;
        processingStartedAt = now;
        processingErrorCode = null;
    }

    public void markCompleted() {
        processingStatus = InterviewTurnProcessingStatus.COMPLETED;
        processingErrorCode = null;
    }

    public void markCompleted(CandidateIntent intent) {
        candidateIntent = intent;
        markCompleted();
    }

    public void markFailed(String errorCode) {
        processingStatus = InterviewTurnProcessingStatus.FAILED;
        processingErrorCode = errorCode;
    }
}
