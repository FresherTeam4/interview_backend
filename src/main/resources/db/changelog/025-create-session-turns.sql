--liquibase formatted sql
--changeset team:025-create-session-turns labels:interview-core

CREATE TABLE session_turns (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT NOT NULL,
    question_id     BIGINT NULL COMMENT 'Script question this turn belongs to; NULL for greeting/closing turns',
    parent_turn_id  BIGINT NULL COMMENT 'A follow-up points back at the answer it digs into',
    turn_index      INT NOT NULL COMMENT 'Order inside the whole session, starting at 0',
    role            VARCHAR(20) NOT NULL,
    input_mode      VARCHAR(20) NOT NULL,
    content_text    TEXT NULL COMMENT 'For a voice CANDIDATE turn this is the final transcript that was scored',
    is_followup     BOOLEAN NOT NULL DEFAULT FALSE,
    followup_depth  INT NOT NULL DEFAULT 0 COMMENT 'At most 2 consecutive follow-ups per topic',
    was_interrupted BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Marks an interviewer turn the user cut off',
    latency_ms      INT NULL COMMENT 'User stops speaking -> AI audio starts. Feeds the p95 < 1200ms target',
    started_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ended_at        DATETIME(6) NULL,

    CONSTRAINT uq_session_turns_index UNIQUE (session_id, turn_index),
    CONSTRAINT fk_session_turns_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_session_turns_question
        FOREIGN KEY (question_id) REFERENCES session_questions (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_session_turns_parent
        FOREIGN KEY (parent_turn_id) REFERENCES session_turns (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_session_turns_role CHECK (role IN ('INTERVIEWER', 'CANDIDATE')),
    CONSTRAINT chk_session_turns_input_mode CHECK (
        input_mode IN ('TEXT', 'VOICE_TURN_BASED', 'VOICE_REALTIME')
    ),
    CONSTRAINT chk_session_turns_turn_index CHECK (turn_index >= 0),
    CONSTRAINT chk_session_turns_followup_depth CHECK (followup_depth BETWEEN 0 AND 2),
    CONSTRAINT chk_session_turns_ended_at CHECK (ended_at IS NULL OR ended_at >= started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_session_turns_parent ON session_turns (parent_turn_id);

--rollback DROP TABLE session_turns;

--changeset team:025-create-turn-audio-assets labels:interview-core
--comment Consider a job that deletes recordings after N days; transcripts stay so history remains readable.

CREATE TABLE turn_audio_assets (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    turn_id     BIGINT NOT NULL,
    kind        VARCHAR(20) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    format      VARCHAR(20) NOT NULL COMMENT 'webm | wav | mp3',
    duration_ms INT NULL,
    size_bytes  BIGINT NULL,
    sample_rate INT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_turn_audio_assets_turn
        FOREIGN KEY (turn_id) REFERENCES session_turns (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_turn_audio_assets_kind CHECK (kind IN ('USER_RECORDING', 'AI_TTS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_turn_audio_assets_turn_kind ON turn_audio_assets (turn_id, kind);

--rollback DROP TABLE turn_audio_assets;

--changeset team:025-create-turn-transcripts labels:interview-core
--comment Both raw_text and edited_text are kept so STT quality on Vietnamese speech can be compared later.

CREATE TABLE turn_transcripts (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    turn_id        BIGINT NOT NULL,
    raw_text       TEXT NOT NULL COMMENT 'Original STT output - never overwritten',
    edited_text    TEXT NULL COMMENT 'User correction. NULL = untouched',
    is_edited      BOOLEAN NOT NULL DEFAULT FALSE,
    edited_at      DATETIME(6) NULL,
    stt_provider   VARCHAR(50) NOT NULL,
    stt_confidence DECIMAL(4,3) NULL,
    language_code  VARCHAR(10) NOT NULL DEFAULT 'vi',
    word_timings   JSON NULL COMMENT 'Word-level timings, required by speech analysis - confirm the STT provider returns them',
    duration_ms    INT NULL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_turn_transcripts_turn UNIQUE (turn_id),
    CONSTRAINT fk_turn_transcripts_turn
        FOREIGN KEY (turn_id) REFERENCES session_turns (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_turn_transcripts_confidence CHECK (
        stt_confidence IS NULL OR stt_confidence BETWEEN 0 AND 1
    ),
    CONSTRAINT chk_turn_transcripts_edited CHECK (
        is_edited = FALSE OR edited_text IS NOT NULL
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE turn_transcripts;
