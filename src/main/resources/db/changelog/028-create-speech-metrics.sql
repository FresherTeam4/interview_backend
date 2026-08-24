--liquibase formatted sql
--changeset team:028-create-filler-word-dictionary labels:interview-core
--comment Kept in the database rather than hardcoded so new words can be added without a redeploy.

CREATE TABLE filler_word_dictionary (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    language_code VARCHAR(10) NOT NULL,
    word          VARCHAR(50) NOT NULL COMMENT 'um, uh, like, you know, ...',
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_filler_word_dictionary_word UNIQUE (language_code, word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE filler_word_dictionary;

--changeset team:028-create-turn-speech-metrics labels:interview-core

CREATE TABLE turn_speech_metrics (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    turn_id          BIGINT NOT NULL,
    words_per_minute DECIMAL(6,2) NULL,
    word_count       INT NULL,
    filler_count     INT NOT NULL DEFAULT 0,
    speaking_ms      INT NULL,
    silence_ms       INT NOT NULL DEFAULT 0,
    longest_pause_ms INT NULL,
    computed_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_turn_speech_metrics_turn UNIQUE (turn_id),
    CONSTRAINT fk_turn_speech_metrics_turn
        FOREIGN KEY (turn_id) REFERENCES session_turns (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_turn_speech_metrics_counts CHECK (
        filler_count >= 0 AND silence_ms >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE turn_speech_metrics;

--changeset team:028-create-turn-filler-occurrences labels:interview-core

CREATE TABLE turn_filler_occurrences (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    turn_id        BIGINT NOT NULL,
    word           VARCHAR(50) NOT NULL,
    occurred_at_ms INT NOT NULL COMMENT 'Position in the recording, for highlighting on the transcript',

    CONSTRAINT fk_turn_filler_occurrences_turn
        FOREIGN KEY (turn_id) REFERENCES session_turns (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_turn_filler_occurrences_position CHECK (occurred_at_ms >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_turn_filler_occurrences_turn ON turn_filler_occurrences (turn_id);

--rollback DROP TABLE turn_filler_occurrences;

--changeset team:028-create-session-speech-metrics labels:interview-core
--comment Objectively measurable numbers only. No confidence_score / attitude_score column: inferring personality from a voice is a sensitive area and skews with regional accents.

CREATE TABLE session_speech_metrics (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id           BIGINT NOT NULL,
    words_per_minute     DECIMAL(6,2) NULL,
    total_word_count     INT NULL,
    filler_count         INT NOT NULL DEFAULT 0,
    filler_per_100_words DECIMAL(6,2) NULL,
    user_speaking_ms     INT NULL,
    total_silence_ms     INT NULL,
    session_duration_ms  INT NULL,
    user_talk_ratio      DECIMAL(4,3) NULL COMMENT 'User speaking time over total session time',
    reference_source     VARCHAR(255) NOT NULL COMMENT 'Where the reference range comes from - mandatory, never blank',
    computed_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_session_speech_metrics_session UNIQUE (session_id),
    CONSTRAINT fk_session_speech_metrics_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_session_speech_metrics_talk_ratio CHECK (
        user_talk_ratio IS NULL OR user_talk_ratio BETWEEN 0 AND 1
    ),
    CONSTRAINT chk_session_speech_metrics_filler CHECK (filler_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE session_speech_metrics;
