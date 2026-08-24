--liquibase formatted sql
--changeset team:021-create-cv-documents labels:interview-core

CREATE TABLE cv_documents (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT NOT NULL,
    storage_key       VARCHAR(500) NOT NULL COMMENT 'Object storage path (S3/MinIO); the file itself is never stored in the database',
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
    file_size_bytes   BIGINT NOT NULL COMMENT 'Max 5MB, enforced in the application layer',
    checksum_sha256   VARCHAR(64) NULL COMMENT 'Detects a re-upload of the same file so a paid re-parse can be skipped',
    status            VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    status_message    TEXT NULL COMMENT 'User-facing reason when status = FAILED',
    is_active         BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'One active CV per user; a new upload deactivates the previous row instead of deleting it',
    uploaded_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    parsed_at         DATETIME(6) NULL,

    CONSTRAINT fk_cv_documents_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_cv_documents_status CHECK (
        status IN ('UPLOADED', 'PARSING', 'PARSED', 'FAILED')
    ),
    CONSTRAINT chk_cv_documents_size CHECK (file_size_bytes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cv_documents_user_active ON cv_documents (user_id, is_active);
CREATE INDEX idx_cv_documents_status ON cv_documents (status);

--rollback DROP TABLE cv_documents;

--changeset team:021-create-cv-parse-results labels:interview-core

CREATE TABLE cv_parse_results (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    cv_document_id BIGINT NOT NULL COMMENT 'A document is parsed exactly once',
    raw_json       JSON NOT NULL COMMENT 'AI output in the fixed parse schema. IMMUTABLE - kept to compare against when a user reports a wrong parse',
    schema_version VARCHAR(20) NOT NULL,
    model_name     VARCHAR(100) NOT NULL COMMENT 'Which model produced this, needed when comparing quality across models',
    duration_ms    INT NULL COMMENT 'Target: under 30 seconds',
    token_cost     INT NULL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_cv_parse_results_document UNIQUE (cv_document_id),
    CONSTRAINT fk_cv_parse_results_document
        FOREIGN KEY (cv_document_id) REFERENCES cv_documents (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE cv_parse_results;
