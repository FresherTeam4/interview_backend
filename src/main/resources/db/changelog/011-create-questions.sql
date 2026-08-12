--liquibase formatted sql
--changeset nxt:011-create-questions labels:sprint-1
-- MySQL 8.0.16+ / Liquibase
-- US-04 schema core and US-10 Question Bank CRUD.

CREATE TABLE questions (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    content_vi      TEXT NOT NULL,
    content_en      TEXT NULL,
    tech_stack_id   INT UNSIGNED NULL COMMENT 'NULL for general behavioral/case-study questions',
    level           ENUM('FRESHER', 'JUNIOR', 'MID', 'SENIOR') NOT NULL,
    question_type   ENUM('BEHAVIORAL', 'TECHNICAL', 'CASE_STUDY') NOT NULL,
    difficulty      ENUM('EASY', 'MEDIUM', 'HARD') NOT NULL DEFAULT 'MEDIUM',
    company_ref     VARCHAR(150) NULL,
    created_by      BIGINT NULL COMMENT 'NULL for system seed data',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    version         INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version for concurrent admin edits',
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_questions_tech_stack
        FOREIGN KEY (tech_stack_id) REFERENCES tech_stacks (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_questions_created_by
        FOREIGN KEY (created_by) REFERENCES user_accounts (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_questions_content_vi CHECK (CHAR_LENGTH(TRIM(content_vi)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_questions_filter
    ON questions (is_active, tech_stack_id, level, question_type, difficulty);
CREATE INDEX idx_questions_created_by ON questions (created_by);
CREATE FULLTEXT INDEX ft_questions_content ON questions (content_vi, content_en);

--rollback DROP TABLE questions;
