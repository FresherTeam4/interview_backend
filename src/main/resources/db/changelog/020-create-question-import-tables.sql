--liquibase formatted sql
--changeset nxt:020-create-question-import-tables labels:question-import
-- Persistent business-level job/row state for preview, partial success and restart recovery.

CREATE TABLE question_import_jobs (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    original_file_name VARCHAR(255) NOT NULL,
    file_sha256     CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status          VARCHAR(40) NOT NULL DEFAULT 'UPLOADED',
    total_rows      INT UNSIGNED NOT NULL DEFAULT 0,
    valid_rows      INT UNSIGNED NOT NULL DEFAULT 0,
    invalid_rows    INT UNSIGNED NOT NULL DEFAULT 0,
    duplicate_rows  INT UNSIGNED NOT NULL DEFAULT 0,
    imported_rows   INT UNSIGNED NOT NULL DEFAULT 0,
    failed_rows     INT UNSIGNED NOT NULL DEFAULT 0,
    created_by      BIGINT NOT NULL,
    started_at      DATETIME(6) NULL,
    completed_at    DATETIME(6) NULL,
    error_message   VARCHAR(1000) NULL,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                  ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_question_import_jobs_creator
        FOREIGN KEY (created_by) REFERENCES user_accounts (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_question_import_job_counts CHECK (
        total_rows >= invalid_rows + duplicate_rows
        AND imported_rows <= valid_rows
        AND failed_rows <= valid_rows
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_question_import_jobs_creator_created
    ON question_import_jobs (created_by, created_at DESC);
CREATE INDEX idx_question_import_jobs_status
    ON question_import_jobs (status, updated_at);

CREATE TABLE question_import_rows (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    import_job_id         BIGINT UNSIGNED NOT NULL,
    csv_row_number        INT UNSIGNED NOT NULL,
    content_vi            MEDIUMTEXT NULL,
    content_en            MEDIUMTEXT NULL,
    level_value           TEXT NULL,
    question_type_value   TEXT NULL,
    difficulty_value      TEXT NULL,
    company_ref           TEXT NULL,
    tech_stack_codes      TEXT NULL,
    technology_codes      TEXT NULL,
    active_value          TEXT NULL,
    content_fingerprint   CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status                VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    error_code            VARCHAR(60) NULL,
    error_message         VARCHAR(1000) NULL,
    existing_question_id  BIGINT UNSIGNED NULL,
    created_question_id   BIGINT UNSIGNED NULL,
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                          ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_question_import_job_row UNIQUE (import_job_id, csv_row_number),
    CONSTRAINT fk_question_import_rows_job
        FOREIGN KEY (import_job_id) REFERENCES question_import_jobs (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_question_import_rows_existing_question
        FOREIGN KEY (existing_question_id) REFERENCES questions (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_question_import_rows_created_question
        FOREIGN KEY (created_question_id) REFERENCES questions (id)
        ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_question_import_rows_job_status
    ON question_import_rows (import_job_id, status, csv_row_number);
CREATE INDEX idx_question_import_rows_fingerprint
    ON question_import_rows (content_fingerprint);

--rollback DROP TABLE question_import_rows;
--rollback DROP TABLE question_import_jobs;
