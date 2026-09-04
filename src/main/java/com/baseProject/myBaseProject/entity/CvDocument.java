package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.CvDocumentStatus;

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

import java.time.Instant;

/**
 * An uploaded CV file. Holds the file's metadata only; the bytes live in object storage
 * under {@link #storageKey}.
 *
 * <p>A user keeps several CVs at once and picks which one an interview runs against, so
 * uploading a new CV leaves the existing rows alone. {@code active} is a soft-delete
 * flag: removing a CV flips it to false and the row stays, because the profile built
 * from it and any interview session that used it still point here.
 */
@Entity
@Table(
        name = "cv_documents",
        indexes = {
                @Index(name = "idx_cv_documents_user_active", columnList = "user_id, is_active"),
                @Index(name = "idx_cv_documents_status", columnList = "status"),
                @Index(name = "idx_cv_documents_user_checksum", columnList = "user_id, checksum_sha256")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    @Builder.Default
    private String contentType = "application/pdf";

    /** The 5MB cap is enforced in the application layer, not by the database. */
    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    /** Lowercase hex SHA-256, used to skip a paid re-parse of a file already seen. */
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CvDocumentStatus status = CvDocumentStatus.UPLOADED;

    /** User-facing message when {@link #status} is {@code FAILED}. */
    @Column(name = "status_message", columnDefinition = "TEXT")
    private String statusMessage;

    /** Maps {@code is_active}. False means the user removed this CV — a soft delete. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "parsed_at")
    private Instant parsedAt;

    public boolean isParsed() {
        return status == CvDocumentStatus.PARSED;
    }

    public boolean isFailed() {
        return status == CvDocumentStatus.FAILED;
    }

    public void reactivate() {
        active = true;
    }

    public void prepareForRetry() {
        status = CvDocumentStatus.UPLOADED;
        statusMessage = null;
    }

    public void markParsing() {
        status = CvDocumentStatus.PARSING;
        statusMessage = null;
    }

    public void markParsed(Instant completedAt) {
        status = CvDocumentStatus.PARSED;
        statusMessage = null;
        if (parsedAt == null) {
            parsedAt = completedAt;
        }
    }

    public void markFailed(String message) {
        status = CvDocumentStatus.FAILED;
        statusMessage = message;
    }
}
