package com.baseProject.myBaseProject.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "session_scores",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_session_scores_session_criterion",
                        columnNames = {"session_id", "criterion_id"})
        },
        indexes = {
                @Index(name = "idx_session_scores_criterion_id", columnList = "criterion_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false, updatable = false)
    private RubricCriterion criterion;

    @Column(name = "criterion_code", nullable = false, length = 50, updatable = false)
    private String criterionCode;

    @Column(name = "criterion_name", nullable = false, length = 150, updatable = false)
    private String criterionName;

    @Column(nullable = false, precision = 4, scale = 2, updatable = false)
    private BigDecimal score;

    @Column(name = "max_score", nullable = false, precision = 4, scale = 2, updatable = false)
    private BigDecimal maxScore;

    @Column(name = "level_no", nullable = false, updatable = false)
    private short levelNo;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String comment;

    @Column(name = "model_name", nullable = false, length = 100, updatable = false)
    private String modelName;

    @Column(name = "scored_at", nullable = false, updatable = false)
    private Instant scoredAt;

    public static SessionScore create(
            InterviewSession session,
            RubricCriterion criterion,
            BigDecimal score,
            BigDecimal maxScore,
            short levelNo,
            String comment,
            String modelName,
            Instant scoredAt) {
        SessionScore result = new SessionScore();
        result.session = session;
        result.criterion = criterion;
        result.criterionCode = criterion.getCode();
        result.criterionName = criterion.getName();
        result.score = score;
        result.maxScore = maxScore;
        result.levelNo = levelNo;
        result.comment = comment;
        result.modelName = modelName;
        result.scoredAt = scoredAt;
        return result;
    }
}
