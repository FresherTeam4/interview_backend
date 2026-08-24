package com.baseProject.myBaseProject.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Score for one criterion of one session. commentText is mandatory: a criterion the model
 * cannot explain must not silently become a zero.
 */
@Entity
@Table(
        name = "session_scores",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_session_scores_session_criterion",
                columnNames = {"session_id", "criterion_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false)
    private RubricCriterion criterion;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal score;

    /** Snapshot of the criterion ceiling so a report renders without re-reading the rubric. */
    @Column(name = "max_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal maxScore;

    /** Which criterion level the answer landed on. */
    @Column(name = "level_no")
    private Integer levelNo;

    @Column(name = "comment_text", nullable = false, columnDefinition = "TEXT")
    private String commentText;

    @Column(name = "scored_at", nullable = false, updatable = false)
    private Instant scoredAt;

    @Column(name = "model_name", length = 100)
    private String modelName;
}
