-- Job description ingestion and immutable-after-confirm interview templates.
-- MySQL 8.0.16+, InnoDB. Apply to a schema with user_accounts(id BIGINT signed).
-- The tables below must not already exist.

CREATE TABLE job_description_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    source_type VARCHAR(10) NOT NULL,
    storage_key VARCHAR(500) NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    source_text MEDIUMTEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    error_code VARCHAR(80) NULL,
    failure_stage VARCHAR(20) NULL,
    status_message TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    uploaded_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_jd_owner_pair (id, owner_id),
    KEY idx_jd_owner_active (owner_id, is_active),
    KEY idx_jd_status (status),
    KEY idx_jd_owner_checksum (owner_id, checksum_sha256),
    CONSTRAINT fk_jd_owner FOREIGN KEY (owner_id)
        REFERENCES user_accounts (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_jd_source_type CHECK (source_type IN ('FILE','TEXT')),
    CONSTRAINT chk_jd_status CHECK (
        status IN ('UPLOADED','EXTRACTING','ANALYZING','READY','FAILED')),
    CONSTRAINT chk_jd_failure_stage CHECK (
        failure_stage IS NULL OR failure_stage IN ('DISPATCH','EXTRACTION','ANALYSIS')),
    CONSTRAINT chk_jd_source CHECK (
        (source_type = 'FILE' AND storage_key IS NOT NULL AND source_text IS NULL)
        OR (source_type = 'TEXT' AND storage_key IS NULL AND source_text IS NOT NULL)),
    CONSTRAINT chk_jd_size CHECK (file_size_bytes >= 0),
    CONSTRAINT chk_jd_result_status CHECK (
        (status = 'READY' AND processed_at IS NOT NULL
            AND error_code IS NULL AND failure_stage IS NULL)
        OR (status = 'FAILED' AND error_code IS NOT NULL AND failure_stage IS NOT NULL)
        OR (status IN ('UPLOADED','EXTRACTING','ANALYZING')
            AND error_code IS NULL AND failure_stage IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE job_description_analysis_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_description_id BIGINT NOT NULL,
    extracted_text MEDIUMTEXT NOT NULL,
    analysis_json JSON NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    duration_ms INT NULL,
    token_count INT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_jd_analysis_document (job_description_id),
    CONSTRAINT fk_jd_analysis_document FOREIGN KEY (job_description_id)
        REFERENCES job_description_documents (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_jd_analysis_metrics CHECK (
        (duration_ms IS NULL OR duration_ms >= 0)
        AND (token_count IS NULL OR token_count >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE interview_templates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    source_job_description_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    job_title VARCHAR(150) NULL,
    target_seniority VARCHAR(100) NULL,
    content_json JSON NOT NULL,
    content_schema_version VARCHAR(20) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    published_at DATETIME(6) NULL,
    archived_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_template_source_jd (source_job_description_id),
    KEY idx_template_owner (owner_id, created_at),
    KEY idx_template_public (published_at, archived_at),
    CONSTRAINT fk_template_owner FOREIGN KEY (owner_id)
        REFERENCES user_accounts (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_template_source_jd_owner FOREIGN KEY (source_job_description_id, owner_id)
        REFERENCES job_description_documents (id, owner_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_template_version CHECK (version >= 0),
    CONSTRAINT chk_template_publish CHECK (
        published_at IS NULL OR (confirmed_at IS NOT NULL AND archived_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- New interview sessions must reference interview_templates(id) directly.
-- The application only accepts templates with confirmed_at IS NOT NULL.
