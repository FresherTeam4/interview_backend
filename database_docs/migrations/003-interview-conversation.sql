ALTER TABLE interview_sessions
    ADD COLUMN end_reason VARCHAR(30) NULL AFTER last_activity_at,
    ADD COLUMN ended_at DATETIME(6) NULL AFTER end_reason,
    ADD CONSTRAINT chk_interview_session_end_reason CHECK (
        end_reason IS NULL OR end_reason IN (
            'AI_COMPLETED', 'TIME_EXPIRED', 'CANDIDATE_FINISHED', 'SYSTEM_TERMINATED'));

CREATE TABLE interview_turns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    reply_to_turn_id BIGINT NULL,
    turn_index INT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content_text TEXT NOT NULL,
    candidate_intent VARCHAR(40) NULL,
    action VARCHAR(20) NULL,
    focus_area_code VARCHAR(50) NULL,
    idempotency_key VARCHAR(100) NULL,
    processing_status VARCHAR(20) NULL,
    processing_started_at DATETIME(6) NULL,
    processing_error_code VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_interview_turn_index UNIQUE (session_id, turn_index),
    CONSTRAINT uq_interview_turn_idempotency UNIQUE (session_id, idempotency_key),
    CONSTRAINT uq_interview_turn_reply_to UNIQUE (reply_to_turn_id),
    CONSTRAINT fk_interview_turn_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_interview_turn_reply_to
        FOREIGN KEY (reply_to_turn_id) REFERENCES interview_turns (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    KEY idx_interview_turn_session_created (session_id, created_at),
    CONSTRAINT chk_interview_turn_index CHECK (turn_index >= 0),
    CONSTRAINT chk_interview_turn_role CHECK (role IN ('INTERVIEWER', 'CANDIDATE')),
    CONSTRAINT chk_interview_turn_intent CHECK (
        candidate_intent IS NULL OR candidate_intent IN (
            'ANSWER', 'REQUEST_REPEAT', 'REQUEST_CLARIFICATION', 'REQUEST_TIME',
            'ASK_INTERVIEWER', 'CANNOT_ANSWER', 'DECLINE_OR_SKIP',
            'CORRECT_PREVIOUS_ANSWER', 'REQUEST_END', 'SOCIAL_OR_META',
            'OFF_TOPIC', 'INAPPROPRIATE', 'OTHER')),
    CONSTRAINT chk_interview_turn_action CHECK (
        action IS NULL OR action IN (
            'OPENING', 'EXPLORE', 'FOLLOW_UP', 'HANDLE_REQUEST', 'CLOSE')),
    CONSTRAINT chk_interview_turn_processing CHECK (
        processing_status IS NULL OR processing_status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_interview_turn_shape CHECK (
        (role = 'INTERVIEWER' AND candidate_intent IS NULL AND action IS NOT NULL
            AND idempotency_key IS NULL AND processing_status IS NULL)
        OR
        (role = 'CANDIDATE' AND action IS NULL AND idempotency_key IS NOT NULL
            AND processing_status IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
