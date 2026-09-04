package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.ReportResultStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "session_reports",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_session_reports_session",
                        columnNames = "session_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private InterviewSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 40, updatable = false)
    private ReportResultStatus resultStatus;

    @Column(name = "overall_score", precision = 5, scale = 2, updatable = false)
    private BigDecimal overallScore;

    @Column(name = "is_partial", nullable = false, updatable = false)
    private boolean partial;

    @Column(name = "completion_ratio", nullable = false, precision = 5, scale = 4, updatable = false)
    private BigDecimal completionRatio;

    @Column(name = "assessed_weight", nullable = false, precision = 4, scale = 3, updatable = false)
    private BigDecimal assessedWeight;

    @Column(name = "summary_text", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String disclaimer;

    @Column(name = "language_code", nullable = false, length = 10, updatable = false)
    private String languageCode;

    @Column(name = "model_name", length = 100, updatable = false)
    private String modelName;

    @Column(name = "prompt_version", length = 20, updatable = false)
    private String promptVersion;

    @Column(name = "duration_ms", updatable = false)
    private Integer durationMs;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    public static SessionReport scored(
            InterviewSession session,
            BigDecimal overallScore,
            boolean partial,
            BigDecimal completionRatio,
            BigDecimal assessedWeight,
            String summaryText,
            String disclaimer,
            String modelName,
            String promptVersion,
            int durationMs,
            Instant generatedAt) {
        SessionReport report = base(
                session,
                ReportResultStatus.SCORED,
                partial,
                completionRatio,
                assessedWeight,
                summaryText,
                disclaimer,
                generatedAt);
        report.overallScore = overallScore;
        report.modelName = modelName;
        report.promptVersion = promptVersion;
        report.durationMs = durationMs;
        return report;
    }

    public static SessionReport insufficientEvidence(
            InterviewSession session,
            String summaryText,
            String disclaimer,
            Instant generatedAt) {
        return base(
                session,
                ReportResultStatus.INSUFFICIENT_EVIDENCE,
                true,
                BigDecimal.ZERO.setScale(4),
                BigDecimal.ZERO.setScale(3),
                summaryText,
                disclaimer,
                generatedAt);
    }

    private static SessionReport base(
            InterviewSession session,
            ReportResultStatus status,
            boolean partial,
            BigDecimal completionRatio,
            BigDecimal assessedWeight,
            String summaryText,
            String disclaimer,
            Instant generatedAt) {
        SessionReport report = new SessionReport();
        report.session = session;
        report.resultStatus = status;
        report.partial = partial;
        report.completionRatio = completionRatio;
        report.assessedWeight = assessedWeight;
        report.summaryText = summaryText;
        report.disclaimer = disclaimer;
        report.languageCode = session.getLanguageCode();
        report.generatedAt = generatedAt;
        return report;
    }
}
