package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.JobDescriptionSourceType;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;

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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "job_descriptions",
        indexes = {
                @Index(
                        name = "idx_job_descriptions_user_active_created",
                        columnList = "user_id, is_active, created_at"),
                @Index(
                        name = "idx_job_descriptions_user_checksum",
                        columnList = "user_id, checksum_sha256")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobDescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 10, updatable = false)
    private JobDescriptionSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JobDescriptionStatus status;

    @Column(name = "original_filename", length = 255, updatable = false)
    private String originalFilename;

    @Column(name = "storage_key", length = 500, updatable = false)
    private String storageKey;

    @Column(name = "content_type", length = 100, updatable = false)
    private String contentType;

    @Column(name = "file_size_bytes", updatable = false)
    private Long fileSizeBytes;

    @Column(
            name = "checksum_sha256",
            nullable = false,
            length = 64,
            updatable = false,
            columnDefinition = "CHAR(64)")
    private String checksumSha256;

    @Column(name = "raw_text", nullable = false, updatable = false, columnDefinition = "MEDIUMTEXT")
    private String rawText;

    @Column(name = "confirmed_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String confirmedText;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Tạo JD dạng text ở trạng thái DRAFT. */
    public static JobDescription createText(
            UserAccount user,
            String title,
            String checksumSha256,
            String text,
            Instant now) {
        return createDraft(
                user,
                title,
                JobDescriptionSourceType.TEXT,
                null,
                checksumSha256,
                text,
                now);
    }

    /** Tạo JD dạng file ở trạng thái DRAFT cùng metadata của object đã upload. */
    public static JobDescription createFile(
            UserAccount user,
            String title,
            FileMetadata file,
            String checksumSha256,
            String text,
            Instant now) {
        return createDraft(
                user,
                title,
                JobDescriptionSourceType.FILE,
                file,
                checksumSha256,
                text,
                now);
    }

    /** Khởi tạo các field chung của một JD mới. */
    private static JobDescription createDraft(
            UserAccount user,
            String title,
            JobDescriptionSourceType sourceType,
            FileMetadata file,
            String checksumSha256,
            String text,
            Instant now) {
        JobDescription jobDescription = new JobDescription();
        jobDescription.user = user;
        jobDescription.title = title;
        jobDescription.sourceType = sourceType;
        jobDescription.status = JobDescriptionStatus.DRAFT;
        if (file != null) {
            jobDescription.originalFilename = file.originalFilename();
            jobDescription.storageKey = file.storageKey();
            jobDescription.contentType = file.contentType();
            jobDescription.fileSizeBytes = file.fileSizeBytes();
        }
        jobDescription.checksumSha256 = checksumSha256;
        jobDescription.rawText = text;
        jobDescription.confirmedText = text;
        jobDescription.active = true;
        jobDescription.createdAt = now;
        jobDescription.updatedAt = now;
        return jobDescription;
    }

    public boolean isDraft() {
        return status == JobDescriptionStatus.DRAFT;
    }

    public void updateDraft(String newTitle, String newConfirmedText, Instant now) {
        title = newTitle;
        confirmedText = newConfirmedText;
        updatedAt = now;
    }

    public void confirm(Instant now) {
        if (!isDraft()) {
            return;
        }
        status = JobDescriptionStatus.READY;
        confirmedAt = now;
        updatedAt = now;
    }

    public void deactivate(Instant now) {
        active = false;
        updatedAt = now;
    }

    public record FileMetadata(
            String originalFilename,
            String storageKey,
            String contentType,
            long fileSizeBytes) {
    }
}
