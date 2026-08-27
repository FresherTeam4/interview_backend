--liquibase formatted sql
--changeset tvt:029-create-session-questions labels:interview-engine-mvp
-- MySQL 8.0.16+ / Liquibase
-- M05 owns the immutable, validated base-question script of an interview session.

CREATE TABLE session_questions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT NOT NULL,
    ordinal             SMALLINT NOT NULL,
    question_text       TEXT NOT NULL,
    topic               VARCHAR(150) NOT NULL,
    competency          VARCHAR(100) NOT NULL,
    difficulty          SMALLINT NOT NULL,
    source_type         VARCHAR(30) NOT NULL,
    source_project_id   BIGINT NULL,
    source_skill_id     BIGINT NULL,
    source_jd_excerpt   TEXT NULL,
    question_signature  CHAR(64) NOT NULL,
    generation_seed     CHAR(36) NOT NULL,
    prompt_version      VARCHAR(20) NOT NULL,
    model_name          VARCHAR(100) NOT NULL,
    created_at          DATETIME(6) NOT NULL,

    CONSTRAINT uq_session_questions_session_ordinal
        UNIQUE (session_id, ordinal),
    CONSTRAINT uq_session_questions_session_signature
        UNIQUE (session_id, question_signature),
    CONSTRAINT fk_session_questions_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_session_questions_project
        FOREIGN KEY (source_project_id) REFERENCES profile_projects (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_session_questions_skill
        FOREIGN KEY (source_skill_id) REFERENCES profile_skills (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_session_questions_ordinal CHECK (ordinal BETWEEN 1 AND 7),
    CONSTRAINT chk_session_questions_text CHECK (
        CHAR_LENGTH(TRIM(question_text)) > 0
    ),
    CONSTRAINT chk_session_questions_topic CHECK (
        CHAR_LENGTH(TRIM(topic)) > 0
    ),
    CONSTRAINT chk_session_questions_competency CHECK (
        CHAR_LENGTH(TRIM(competency)) > 0
    ),
    CONSTRAINT chk_session_questions_difficulty CHECK (difficulty BETWEEN 1 AND 5),
    CONSTRAINT chk_session_questions_source_type CHECK (
        source_type IN (
            'CV_PROJECT', 'CV_SKILL', 'CV_JD_MATCH', 'JD_GAP', 'GENERAL_BEHAVIORAL'
        )
    ),
    CONSTRAINT chk_session_questions_signature CHECK (
        question_signature REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_session_questions_generation_seed CHECK (
        generation_seed REGEXP
        '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_session_questions_prompt_version CHECK (
        CHAR_LENGTH(TRIM(prompt_version)) > 0
    ),
    CONSTRAINT chk_session_questions_model_name CHECK (
        CHAR_LENGTH(TRIM(model_name)) > 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_session_questions_project_id
    ON session_questions (source_project_id);
CREATE INDEX idx_session_questions_skill_id
    ON session_questions (source_skill_id);

--rollback DROP TABLE session_questions;
