--liquibase formatted sql
--changeset team:026-create-session-voice-connections labels:interview-core

CREATE TABLE session_voice_connections (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id              BIGINT NOT NULL,
    connected_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    disconnected_at         DATETIME(6) NULL,
    client_platform         VARCHAR(50) NULL COMMENT 'chrome-desktop | safari-ios ...',
    fell_back_to_turn_based BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'On a dropped realtime link the session falls back to turn-based and keeps its context',
    p50_latency_ms          INT NULL,
    p95_latency_ms          INT NULL COMMENT 'Target < 1200ms - measured, never estimated',
    disconnect_reason       VARCHAR(255) NULL,

    CONSTRAINT fk_session_voice_connections_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_session_voice_connections_window CHECK (
        disconnected_at IS NULL OR disconnected_at >= connected_at
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_session_voice_connections_session ON session_voice_connections (session_id);

--rollback DROP TABLE session_voice_connections;
