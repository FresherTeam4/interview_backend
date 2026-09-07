package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.InterviewAssessmentConfidence;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "interview_assessments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_interview_assessment_session",
                columnNames = "session_id"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewAssessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private InterviewSession session;

    @Column(name = "technical_score", precision = 5, scale = 2)
    private BigDecimal technicalScore;

    @Column(name = "communication_score", precision = 5, scale = 2)
    private BigDecimal communicationScore;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "coverage_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal coveragePercentage;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private InterviewAssessmentConfidence confidence;

    @Column(name = "overall_summary", nullable = false, columnDefinition = "TEXT")
    private String overallSummary;

    @Column(name = "strengths_json", nullable = false, columnDefinition = "JSON")
    private String strengthsJson;

    @Column(name = "improvements_json", nullable = false, columnDefinition = "JSON")
    private String improvementsJson;

    @Column(name = "action_plan_json", nullable = false, columnDefinition = "JSON")
    private String actionPlanJson;

    @Column(name = "communication_feedback", nullable = false, columnDefinition = "TEXT")
    private String communicationFeedback;

    @Column(name = "schema_version", nullable = false, updatable = false, length = 20)
    private String schemaVersion;

    @Column(name = "model_name", nullable = false, updatable = false, length = 100)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, updatable = false, length = 20)
    private String promptVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
