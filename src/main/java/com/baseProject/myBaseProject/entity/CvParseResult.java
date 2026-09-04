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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "cv_parse_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvParseResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cv_document_id", nullable = false, unique = true)
    private CvDocument cvDocument;

    @Column(name = "raw_json", nullable = false, columnDefinition = "JSON")
    private String rawJson;

    @Column(name = "schema_version", nullable = false, length = 20)
    private String schemaVersion;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "token_cost")
    private Integer tokenCost;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
