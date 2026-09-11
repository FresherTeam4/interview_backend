ALTER TABLE interview_sessions
    ADD COLUMN mode VARCHAR(30) NOT NULL DEFAULT 'VOICE_TURN_BASED' AFTER status,
    ADD COLUMN realtime_provider VARCHAR(50) NULL AFTER mode,
    ADD COLUMN realtime_voice_name VARCHAR(50) NULL AFTER realtime_provider,
    ADD CONSTRAINT chk_interview_session_mode CHECK (
        mode IN ('TEXT', 'VOICE_TURN_BASED', 'VOICE_REALTIME'));

ALTER TABLE interview_turns
    ADD COLUMN input_mode VARCHAR(30) NOT NULL DEFAULT 'TEXT' AFTER role,
    ADD COLUMN was_interrupted BOOLEAN NOT NULL DEFAULT FALSE AFTER processing_error_code,
    ADD COLUMN latency_ms INTEGER NULL AFTER was_interrupted,
    ADD CONSTRAINT chk_interview_turn_input_mode CHECK (
        input_mode IN ('TEXT', 'VOICE_TURN_BASED', 'VOICE_REALTIME')),
    ADD CONSTRAINT chk_interview_turn_latency CHECK (
        latency_ms IS NULL OR latency_ms >= 0);

CREATE TABLE interview_voice_connections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    transport VARCHAR(20) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    voice_name VARCHAR(50) NOT NULL,
    external_session_id VARCHAR(255) NULL,
    resumption_handle MEDIUMTEXT NULL,
    client_platform VARCHAR(50) NULL,
    input_sample_rate INTEGER NULL,
    output_sample_rate INTEGER NULL,
    connected_at DATETIME(6) NULL,
    disconnected_at DATETIME(6) NULL,
    disconnect_reason VARCHAR(255) NULL,
    fell_back_to_turn_based BOOLEAN NOT NULL DEFAULT FALSE,
    p50_latency_ms INTEGER NULL,
    p95_latency_ms INTEGER NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_interview_voice_connection_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    KEY idx_interview_voice_connection_session (session_id, created_at),
    KEY idx_interview_voice_connection_active (session_id, disconnected_at),
    CONSTRAINT chk_interview_voice_connection_transport CHECK (
        transport IN ('WEBRTC', 'WEBSOCKET')),
    CONSTRAINT chk_interview_voice_connection_sample_rates CHECK (
        (input_sample_rate IS NULL OR input_sample_rate > 0)
        AND (output_sample_rate IS NULL OR output_sample_rate > 0)),
    CONSTRAINT chk_interview_voice_connection_latency CHECK (
        (p50_latency_ms IS NULL OR p50_latency_ms >= 0)
        AND (p95_latency_ms IS NULL OR p95_latency_ms >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE interview_realtime_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    connection_id BIGINT NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    transcript_text TEXT NULL,
    occurred_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_interview_realtime_event_provider
        UNIQUE (connection_id, provider_event_id),
    CONSTRAINT uq_interview_realtime_event_sequence
        UNIQUE (connection_id, sequence_number),
    CONSTRAINT fk_interview_realtime_event_connection
        FOREIGN KEY (connection_id) REFERENCES interview_voice_connections (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    KEY idx_interview_realtime_event_pending (connection_id, processed_at),
    CONSTRAINT chk_interview_realtime_event_sequence CHECK (
        sequence_number >= 0),
    CONSTRAINT chk_interview_realtime_event_type CHECK (
        event_type IN (
            'SESSION_CONNECTED', 'SESSION_RESUMPTION_UPDATED',
            'USER_SPEECH_STARTED', 'USER_TRANSCRIPT_PARTIAL',
            'USER_TRANSCRIPT_FINAL', 'ASSISTANT_SPEECH_STARTED',
            'ASSISTANT_TRANSCRIPT_FINAL', 'ASSISTANT_INTERRUPTED',
            'SESSION_DISCONNECTED', 'PROVIDER_ERROR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
