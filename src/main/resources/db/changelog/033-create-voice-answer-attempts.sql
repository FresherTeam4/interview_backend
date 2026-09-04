--liquibase formatted sql
--changeset tvt:033-create-voice-answer-attempts labels:interview-engine-mvp
-- MySQL 8.0.16+ / Liquibase
-- M12 stores validated user recordings without creating candidate turns or starting STT.

CREATE TABLE voice_answer_attempts (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id            BIGINT NOT NULL,
    question_id           BIGINT NOT NULL,
    prompt_turn_id        BIGINT NOT NULL,
    confirmed_turn_id     BIGINT NULL,
    client_attempt_id     VARCHAR(64) NOT NULL,
    attempt_no            SMALLINT NOT NULL,
    status                VARCHAR(30) NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    storage_key           VARCHAR(500) NOT NULL,
    content_type          VARCHAR(100) NOT NULL,
    format                VARCHAR(20) NOT NULL,
    file_size_bytes       BIGINT NOT NULL,
    duration_ms           INT NOT NULL,
    checksum_sha256       CHAR(64) NOT NULL,
    raw_text              MEDIUMTEXT NULL,
    edited_text           MEDIUMTEXT NULL,
    stt_provider          VARCHAR(50) NULL,
    stt_confidence        DECIMAL(4,3) NULL,
    processing_token      CHAR(36) NULL,
    processing_started_at DATETIME(6) NULL,
    status_message        VARCHAR(500) NULL,
    audio_deleted_at      DATETIME(6) NULL,
    created_at            DATETIME(6) NOT NULL,
    transcribed_at        DATETIME(6) NULL,
    confirmed_at          DATETIME(6) NULL,

    CONSTRAINT uq_voice_attempts_session_client
        UNIQUE (session_id, client_attempt_id),
    CONSTRAINT uq_voice_attempts_session_question_no
        UNIQUE (session_id, question_id, attempt_no),
    CONSTRAINT uq_voice_attempts_confirmed_turn
        UNIQUE (confirmed_turn_id),
    CONSTRAINT fk_voice_attempts_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_voice_attempts_question
        FOREIGN KEY (question_id) REFERENCES session_questions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_voice_attempts_prompt_turn
        FOREIGN KEY (prompt_turn_id) REFERENCES session_turns (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_voice_attempts_confirmed_turn
        FOREIGN KEY (confirmed_turn_id) REFERENCES session_turns (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_voice_attempts_number CHECK (attempt_no > 0),
    CONSTRAINT chk_voice_attempts_status CHECK (
        status IN ('RECORDED', 'TRANSCRIBING', 'TRANSCRIBED', 'CONFIRMED', 'DISCARDED', 'FAILED')
    ),
    CONSTRAINT chk_voice_attempts_format CHECK (format IN ('WEBM_OPUS', 'MP4_AAC')),
    CONSTRAINT chk_voice_attempts_file_size CHECK (file_size_bytes > 0),
    CONSTRAINT chk_voice_attempts_duration CHECK (duration_ms > 0),
    CONSTRAINT chk_voice_attempts_confidence CHECK (
        stt_confidence IS NULL OR stt_confidence BETWEEN 0 AND 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_voice_attempts_question_id
    ON voice_answer_attempts (question_id);
CREATE INDEX idx_voice_attempts_prompt_turn_id
    ON voice_answer_attempts (prompt_turn_id);
CREATE INDEX idx_voice_attempts_recovery
    ON voice_answer_attempts (status, processing_started_at);
CREATE INDEX idx_voice_attempts_audio_deleted
    ON voice_answer_attempts (audio_deleted_at);

--rollback DROP TABLE voice_answer_attempts;
