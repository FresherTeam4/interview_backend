--liquibase formatted sql
--changeset tvt:019-create-cv-documents labels:sprint-1
-- MySQL 8.0.16+ / Liquibase
-- Group 2 "CV & Profile". Holds the uploaded file's metadata, never the file bytes.

CREATE TABLE cv_documents (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT NOT NULL,
    storage_key       VARCHAR(500) NOT NULL COMMENT 'Object storage path (S3/MinIO); the PDF itself never goes into the database',
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
    file_size_bytes   BIGINT NOT NULL COMMENT 'Recorded for statistics; the 5MB cap is enforced in the application layer',
    checksum_sha256   VARCHAR(64) NULL COMMENT 'Lowercase hex SHA-256; lets a re-upload of the same file skip a paid parse call',
    status            VARCHAR(20) NOT NULL DEFAULT 'UPLOADED' COMMENT 'Application-managed CvDocumentStatus',
    status_message    TEXT NULL COMMENT 'User-facing message when status = FAILED',
    is_active         BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Soft-delete flag. FALSE = the user removed this CV; the row stays so past interview sessions keep their link',
    uploaded_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    parsed_at         DATETIME(6) NULL,

    CONSTRAINT fk_cv_documents_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_cv_documents_status CHECK (
        status IN ('UPLOADED', 'PARSING', 'PARSED', 'FAILED')
    ),
    CONSTRAINT chk_cv_documents_file_size CHECK (file_size_bytes > 0),
    CONSTRAINT chk_cv_documents_parsed_at CHECK (
        parsed_at IS NULL OR parsed_at >= uploaded_at
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Leftmost column user_id also serves as the index backing fk_cv_documents_user.
CREATE INDEX idx_cv_documents_user_active ON cv_documents (user_id, is_active);
CREATE INDEX idx_cv_documents_status ON cv_documents (status);
CREATE INDEX idx_cv_documents_user_checksum ON cv_documents (user_id, checksum_sha256);

--rollback DROP TABLE cv_documents;
