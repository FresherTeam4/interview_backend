CREATE TABLE interview_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    profile_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    template_title_snapshot VARCHAR(200) NOT NULL,
    profile_name_snapshot VARCHAR(150) NOT NULL,
    status VARCHAR(30) NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    duration_minutes INT NOT NULL,
    interviewer_style VARCHAR(20) NOT NULL,
    template_snapshot_json JSON NOT NULL,
    profile_snapshot_json JSON NOT NULL,
    job_context_summary TEXT NULL,
    candidate_context_summary TEXT NULL,
    opening_message TEXT NULL,
    conversation_summary MEDIUMTEXT NULL,
    current_turn_index INT NOT NULL DEFAULT 0,
    preparation_error_code VARCHAR(80) NULL,
    preparation_error_message TEXT NULL,
    plan_schema_version VARCHAR(20) NULL,
    plan_model_name VARCHAR(100) NULL,
    plan_prompt_version VARCHAR(20) NULL,
    preparation_started_at DATETIME(6) NULL,
    prepared_at DATETIME(6) NULL,
    started_at DATETIME(6) NULL,
    deadline_at DATETIME(6) NULL,
    last_activity_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_interview_session_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_interview_session_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_interview_session_template
        FOREIGN KEY (template_id) REFERENCES interview_templates (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_interview_session_profile
        FOREIGN KEY (profile_id) REFERENCES candidate_profiles (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    KEY idx_interview_session_user_status (user_id, status),
    KEY idx_interview_session_user_created (user_id, created_at),
    KEY idx_interview_session_deadline (deadline_at),
    CONSTRAINT chk_interview_session_status CHECK (status IN (
        'PREPARING', 'READY', 'PREPARATION_FAILED', 'IN_PROGRESS', 'SCORING',
        'COMPLETED', 'SCORING_FAILED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_interview_session_style CHECK (
        interviewer_style IN ('FRIENDLY', 'PROFESSIONAL', 'CHALLENGING')),
    CONSTRAINT chk_interview_session_duration CHECK (duration_minutes > 0),
    CONSTRAINT chk_interview_session_turn_index CHECK (current_turn_index >= 0),
    CONSTRAINT chk_interview_session_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE interview_focus_areas (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT NULL,
    priority VARCHAR(10) NOT NULL,
    reason TEXT NOT NULL,
    planned_seconds INT NOT NULL,
    evidence_status VARCHAR(20) NOT NULL DEFAULT 'NOT_EXPLORED',
    evidence_summary TEXT NULL,
    display_order SMALLINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_interview_focus_area_code UNIQUE (session_id, code),
    CONSTRAINT fk_interview_focus_area_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    KEY idx_interview_focus_area_session_order (session_id, display_order),
    CONSTRAINT chk_interview_focus_area_priority CHECK (
        priority IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_interview_focus_area_evidence_status CHECK (
        evidence_status IN ('NOT_EXPLORED', 'PARTIAL', 'SUFFICIENT')),
    CONSTRAINT chk_interview_focus_area_time CHECK (planned_seconds >= 60),
    CONSTRAINT chk_interview_focus_area_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE interview_session_transitions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(255) NULL,
    actor VARCHAR(20) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_interview_session_transition_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    KEY idx_interview_session_transition_time (session_id, occurred_at),
    CONSTRAINT chk_interview_session_transition_actor CHECK (
        actor IN ('USER', 'SYSTEM', 'SCHEDULER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
