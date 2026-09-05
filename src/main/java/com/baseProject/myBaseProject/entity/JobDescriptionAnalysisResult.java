package com.baseProject.myBaseProject.entity;

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
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Immutable
@Table(name = "job_description_analysis_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_jd_analysis_document", columnNames = "job_description_id"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDescriptionAnalysisResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_description_id", nullable = false, updatable = false)
    private JobDescriptionDocument jobDescription;

    @Column(name = "extracted_text", nullable = false, updatable = false, columnDefinition = "MEDIUMTEXT")
    private String extractedText;

    @Column(name = "analysis_json", nullable = false, updatable = false, columnDefinition = "JSON")
    private String analysisJson;

    @Column(name = "schema_version", nullable = false, updatable = false, length = 20)
    private String schemaVersion;

    @Column(name = "model_name", nullable = false, updatable = false, length = 100)
    private String modelName;

    @Column(name = "duration_ms", updatable = false)
    private Integer durationMs;

    @Column(name = "token_count", updatable = false)
    private Integer tokenCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
