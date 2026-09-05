package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.JobDescriptionSourceType;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.enums.JobDescriptionFailureStage;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "job_description_documents", indexes = {
        @Index(name = "idx_jd_owner_active", columnList = "owner_id, is_active"),
        @Index(name = "idx_jd_status", columnList = "status"),
        @Index(name = "idx_jd_owner_checksum", columnList = "owner_id, checksum_sha256")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDescriptionDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserAccount owner;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source_type", nullable = false, updatable = false, length = 10)
    private JobDescriptionSourceType sourceType;

    @Column(name = "storage_key", updatable = false, length = 500)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, updatable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false, updatable = false)
    private long fileSizeBytes;

    @Column(name = "checksum_sha256", nullable = false, updatable = false, length = 64)
    private String checksumSha256;

    @Column(name = "source_text", updatable = false, columnDefinition = "MEDIUMTEXT")
    private String sourceText;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JobDescriptionStatus status = JobDescriptionStatus.UPLOADED;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "failure_stage", length = 20)
    private JobDescriptionFailureStage failureStage;

    @Column(name = "status_message", columnDefinition = "TEXT")
    private String statusMessage;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public boolean isReady() {
        return status == JobDescriptionStatus.READY;
    }

    public boolean isProcessing() {
        return status == JobDescriptionStatus.UPLOADED
                || status == JobDescriptionStatus.EXTRACTING
                || status == JobDescriptionStatus.ANALYZING;
    }

    public void reactivate() {
        active = true;
    }

    public void prepareForRetry() {
        status = JobDescriptionStatus.UPLOADED;
        errorCode = null;
        failureStage = null;
        statusMessage = null;
        processedAt = null;
    }

    public void markExtracting() {
        status = JobDescriptionStatus.EXTRACTING;
        errorCode = null;
        failureStage = null;
        statusMessage = null;
    }

    public void markAnalyzing() {
        status = JobDescriptionStatus.ANALYZING;
        errorCode = null;
        failureStage = null;
        statusMessage = null;
    }

    public void markReady(Instant completedAt) {
        status = JobDescriptionStatus.READY;
        errorCode = null;
        failureStage = null;
        statusMessage = null;
        processedAt = completedAt;
    }

    public void markFailed(String code, String message) {
        failureStage = switch (status) {
            case EXTRACTING -> JobDescriptionFailureStage.EXTRACTION;
            case ANALYZING -> JobDescriptionFailureStage.ANALYSIS;
            default -> JobDescriptionFailureStage.DISPATCH;
        };
        status = JobDescriptionStatus.FAILED;
        errorCode = code;
        statusMessage = message;
    }

    public void deactivate() {
        active = false;
    }
}
