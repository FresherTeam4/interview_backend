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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The feedback document shown after a session. The disclaimer column is not optional: the
 * report must say it is practice feedback, not a competency certificate.
 */
@Entity
@Table(
        name = "session_reports",
        uniqueConstraints = @UniqueConstraint(name = "uq_session_reports_session", columnNames = "session_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @Column(name = "overall_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String disclaimer;

    @Column(name = "language_code", nullable = false, length = 10)
    @Builder.Default
    private String languageCode = "vi";

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    /** Generation time. Target: under 60 seconds. */
    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;
}
