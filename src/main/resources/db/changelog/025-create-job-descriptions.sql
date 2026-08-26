--liquibase formatted sql
--changeset tvt:025-create-job-descriptions labels:interview-engine-mvp
-- MySQL 8.0.16+ / Liquibase
-- M01 owns the whole JD resource shape. File columns remain NULL until M02 exposes ingestion.

CREATE TABLE job_descriptions (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT NOT NULL,
    title             VARCHAR(200) NOT NULL,
    source_type       VARCHAR(10) NOT NULL,
    status            VARCHAR(10) NOT NULL,
    original_filename VARCHAR(255) NULL,
    storage_key       VARCHAR(500) NULL,
    content_type      VARCHAR(100) NULL,
    file_size_bytes   BIGINT NULL,
    checksum_sha256   CHAR(64) NOT NULL COMMENT 'SHA-256 of normalized source text at creation',
    raw_text          MEDIUMTEXT NOT NULL COMMENT 'Immutable source text pasted or extracted at creation',
    confirmed_text    MEDIUMTEXT NOT NULL COMMENT 'Editable only while status is DRAFT',
    confirmed_at      DATETIME(6) NULL,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,

    CONSTRAINT fk_job_descriptions_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_job_descriptions_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT chk_job_descriptions_source_type CHECK (source_type IN ('TEXT', 'FILE')),
    CONSTRAINT chk_job_descriptions_status CHECK (status IN ('DRAFT', 'READY')),
    CONSTRAINT chk_job_descriptions_checksum CHECK (
        checksum_sha256 REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_job_descriptions_text CHECK (
        CHAR_LENGTH(TRIM(raw_text)) > 0 AND CHAR_LENGTH(TRIM(confirmed_text)) > 0
    ),
    CONSTRAINT chk_job_descriptions_confirmation CHECK (
        (status = 'DRAFT' AND confirmed_at IS NULL)
        OR (status = 'READY' AND confirmed_at IS NOT NULL)
    ),
    CONSTRAINT chk_job_descriptions_source_fields CHECK (
        (
            source_type = 'TEXT'
            AND original_filename IS NULL
            AND storage_key IS NULL
            AND content_type IS NULL
            AND file_size_bytes IS NULL
        )
        OR
        (
            source_type = 'FILE'
            AND original_filename IS NOT NULL
            AND storage_key IS NOT NULL
            AND content_type IS NOT NULL
            AND file_size_bytes IS NOT NULL
            AND file_size_bytes > 0
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_job_descriptions_user_active_created
    ON job_descriptions (user_id, is_active, created_at);
CREATE INDEX idx_job_descriptions_user_checksum
    ON job_descriptions (user_id, checksum_sha256);

--rollback DROP TABLE job_descriptions;
