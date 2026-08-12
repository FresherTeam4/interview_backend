package com.baseProject.myBaseProject.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interview_session_id", nullable = false, unique = true)
    private InterviewSession interviewSession;

    @Column(name = "overall_score", precision = 4, scale = 2)
    private BigDecimal overallScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "star_score_json", columnDefinition = "JSON")
    private String starScoreJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "communication_score_json", columnDefinition = "JSON")
    private String communicationScoreJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "technical_score_json", columnDefinition = "JSON")
    private String technicalScoreJson;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Column(name = "drive_file_url", length = 1000)
    private String driveFileUrl;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
