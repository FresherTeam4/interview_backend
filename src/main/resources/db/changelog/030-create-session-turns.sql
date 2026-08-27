--liquibase formatted sql
--changeset tvt:030-create-session-turns labels:interview-engine-mvp
-- MySQL 8.0.16+ / Liquibase
-- M07 owns the append-only conversation used to start and resume an interview session.

CREATE TABLE session_turns (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id       BIGINT NOT NULL,
    question_id      BIGINT NULL,
    parent_turn_id   BIGINT NULL,
    turn_index       INT NOT NULL,
    role             VARCHAR(20) NOT NULL,
    input_mode       VARCHAR(30) NOT NULL,
    content_text     MEDIUMTEXT NOT NULL,
    client_turn_id   VARCHAR(64) NULL,
    is_followup      BOOLEAN NOT NULL DEFAULT FALSE,
    followup_depth   SMALLINT NOT NULL DEFAULT 0,
    latency_ms       INT NULL,
    started_at       DATETIME(6) NOT NULL,
    ended_at         DATETIME(6) NULL,
    created_at       DATETIME(6) NOT NULL,

    CONSTRAINT uq_session_turns_session_index
        UNIQUE (session_id, turn_index),
    CONSTRAINT uq_session_turns_session_client_turn
        UNIQUE (session_id, client_turn_id),
    CONSTRAINT fk_session_turns_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_session_turns_question
        FOREIGN KEY (question_id) REFERENCES session_questions (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_session_turns_parent
        FOREIGN KEY (parent_turn_id) REFERENCES session_turns (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_session_turns_index CHECK (turn_index >= 0),
    CONSTRAINT chk_session_turns_role CHECK (role IN ('INTERVIEWER', 'CANDIDATE')),
    CONSTRAINT chk_session_turns_input_mode CHECK (
        input_mode IN ('TEXT', 'VOICE_TURN_BASED')
    ),
    CONSTRAINT chk_session_turns_content CHECK (
        CHAR_LENGTH(TRIM(content_text)) > 0
    ),
    CONSTRAINT chk_session_turns_role_fields CHECK (
        (role = 'INTERVIEWER' AND client_turn_id IS NULL)
        OR (
            role = 'CANDIDATE'
            AND client_turn_id IS NOT NULL
            AND CHAR_LENGTH(TRIM(client_turn_id)) > 0
        )
    ),
    CONSTRAINT chk_session_turns_followup CHECK (
        followup_depth BETWEEN 0 AND 2
        AND (is_followup = FALSE OR role = 'INTERVIEWER')
        AND (is_followup = TRUE OR followup_depth = 0)
    ),
    CONSTRAINT chk_session_turns_latency CHECK (latency_ms IS NULL OR latency_ms >= 0),
    CONSTRAINT chk_session_turns_time CHECK (ended_at IS NULL OR ended_at >= started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_session_turns_question_id
    ON session_turns (question_id);
CREATE INDEX idx_session_turns_parent_turn_id
    ON session_turns (parent_turn_id);

--rollback DROP TABLE session_turns;
