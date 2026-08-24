--liquibase formatted sql
--changeset tvt:020-create-cv-parse-results labels:sprint-1
-- MySQL 8.0.16+ / Liquibase
-- Immutable record of what the AI extracted. INSERT only, never UPDATE:
-- it is the reference copy used to tell a bad parse apart from a bad prompt.

CREATE TABLE cv_parse_results (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    cv_document_id BIGINT NOT NULL COMMENT 'A document is parsed once only',
    raw_json       JSON NOT NULL COMMENT 'Verbatim AI output following a fixed schema. Immutable',
    schema_version VARCHAR(20) NOT NULL COMMENT 'Which raw_json layout this row follows',
    model_name     VARCHAR(100) NOT NULL COMMENT 'Which model produced it, needed to compare quality across models',
    duration_ms    INT NULL COMMENT 'Target: under 30 seconds',
    token_cost     INT NULL COMMENT 'API cost tracking',
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_cv_parse_results_document UNIQUE (cv_document_id),
    CONSTRAINT fk_cv_parse_results_document
        FOREIGN KEY (cv_document_id) REFERENCES cv_documents (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_cv_parse_results_duration CHECK (
        duration_ms IS NULL OR duration_ms >= 0
    ),
    CONSTRAINT chk_cv_parse_results_token_cost CHECK (
        token_cost IS NULL OR token_cost >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE cv_parse_results;
