--liquibase formatted sql
--changeset tvt:028-create-interview-session-foundation labels:interview-engine-mvp
-- MySQL 8.0.16+ / Liquibase
-- M04 owns session state, immutable context snapshots and append-only transition history.

CREATE TABLE interview_sessions (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                  BIGINT NOT NULL,
    profile_id               BIGINT NOT NULL,
    job_description_id       BIGINT NOT NULL,
    rubric_version_id        BIGINT NOT NULL,
    creation_key             VARCHAR(128) NOT NULL,
    creation_request_hash    CHAR(64) NOT NULL,
    difficulty               VARCHAR(10) NOT NULL,
    mode                     VARCHAR(30) NOT NULL,
    language_code            VARCHAR(10) NOT NULL,
    status                   VARCHAR(30) NOT NULL,
    awaiting_action          VARCHAR(40) NOT NULL,
    current_question_ordinal SMALLINT NULL,
    next_turn_index          INT NOT NULL DEFAULT 0,
    current_followup_depth   SMALLINT NOT NULL DEFAULT 0,
    total_followup_count     SMALLINT NOT NULL DEFAULT 0,
    answered_question_count  SMALLINT NOT NULL DEFAULT 0,
    total_question_count     SMALLINT NOT NULL DEFAULT 0,
    generation_seed          CHAR(36) NOT NULL,
    version                  BIGINT NOT NULL DEFAULT 0,
    processing_stage         VARCHAR(30) NULL,
    processing_token         CHAR(36) NULL,
    processing_started_at    DATETIME(6) NULL,
    processing_attempts      SMALLINT NOT NULL DEFAULT 0,
    next_retry_at            DATETIME(6) NULL,
    failure_stage            VARCHAR(30) NULL,
    status_message           VARCHAR(500) NULL,
    end_reason               VARCHAR(40) NULL,
    overall_score            DECIMAL(5,2) NULL,
    started_at               DATETIME(6) NULL,
    last_activity_at         DATETIME(6) NOT NULL,
    completed_at             DATETIME(6) NULL,
    created_at               DATETIME(6) NOT NULL,
    updated_at               DATETIME(6) NOT NULL,

    CONSTRAINT uq_interview_sessions_user_creation_key UNIQUE (user_id, creation_key),
    CONSTRAINT fk_interview_sessions_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_interview_sessions_profile
        FOREIGN KEY (profile_id) REFERENCES candidate_profiles (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_interview_sessions_job_description
        FOREIGN KEY (job_description_id) REFERENCES job_descriptions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_interview_sessions_rubric_version
        FOREIGN KEY (rubric_version_id) REFERENCES rubric_versions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_interview_sessions_creation_key CHECK (
        CHAR_LENGTH(TRIM(creation_key)) > 0
    ),
    CONSTRAINT chk_interview_sessions_request_hash CHECK (
        creation_request_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_interview_sessions_difficulty CHECK (
        difficulty IN ('EASY', 'MEDIUM', 'HARD')
    ),
    CONSTRAINT chk_interview_sessions_mode CHECK (
        mode IN ('TEXT', 'VOICE_TURN_BASED')
    ),
    CONSTRAINT chk_interview_sessions_language CHECK (
        CHAR_LENGTH(TRIM(language_code)) > 0
    ),
    CONSTRAINT chk_interview_sessions_status CHECK (
        status IN (
            'CREATED', 'SCRIPT_GENERATING', 'READY', 'IN_PROGRESS', 'PAUSED',
            'SCORING', 'COMPLETED', 'ABANDONED', 'FAILED'
        )
    ),
    CONSTRAINT chk_interview_sessions_awaiting_action CHECK (
        awaiting_action IN (
            'START_SESSION', 'CANDIDATE_ANSWER', 'TRANSCRIPT_CONFIRMATION',
            'ENGINE_RESPONSE', 'ENGINE_RETRY', 'REPORT', 'NONE'
        )
    ),
    CONSTRAINT chk_interview_sessions_awaiting_invariant CHECK (
        (status IN ('CREATED', 'SCRIPT_GENERATING', 'PAUSED', 'COMPLETED', 'ABANDONED')
            AND awaiting_action = 'NONE')
        OR (status = 'READY' AND awaiting_action = 'START_SESSION')
        OR (status = 'IN_PROGRESS' AND awaiting_action IN (
            'CANDIDATE_ANSWER', 'TRANSCRIPT_CONFIRMATION', 'ENGINE_RESPONSE', 'ENGINE_RETRY'
        ))
        OR (status = 'SCORING' AND awaiting_action = 'REPORT')
        OR (status = 'FAILED' AND awaiting_action = 'ENGINE_RETRY')
    ),
    CONSTRAINT chk_interview_sessions_counters CHECK (
        next_turn_index >= 0
        AND current_followup_depth BETWEEN 0 AND 2
        AND total_followup_count BETWEEN 0 AND 5
        AND answered_question_count >= 0
        AND total_question_count >= 0
        AND answered_question_count <= total_question_count
        AND (total_question_count = 0 OR total_question_count BETWEEN 5 AND 7)
        AND (
            current_question_ordinal IS NULL
            OR (
                current_question_ordinal >= 1
                AND current_question_ordinal <= total_question_count
            )
        )
    ),
    CONSTRAINT chk_interview_sessions_generation_seed CHECK (
        generation_seed REGEXP
        '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_interview_sessions_version CHECK (version >= 0),
    CONSTRAINT chk_interview_sessions_processing_stage CHECK (
        processing_stage IS NULL
        OR processing_stage IN ('SCRIPT_GENERATION', 'NEXT_TURN', 'SCORING')
    ),
    CONSTRAINT chk_interview_sessions_processing_claim CHECK (
        (processing_token IS NULL AND processing_started_at IS NULL)
        OR (
            processing_stage IS NOT NULL
            AND processing_token IS NOT NULL
            AND processing_token REGEXP
                '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            AND processing_started_at IS NOT NULL
        )
    ),
    CONSTRAINT chk_interview_sessions_processing_status CHECK (
        processing_stage IS NULL
        OR (processing_stage = 'SCRIPT_GENERATION' AND status = 'SCRIPT_GENERATING')
        OR (processing_stage = 'NEXT_TURN' AND status = 'IN_PROGRESS')
        OR (processing_stage = 'SCORING' AND status = 'SCORING')
    ),
    CONSTRAINT chk_interview_sessions_processing_attempts CHECK (processing_attempts >= 0),
    CONSTRAINT chk_interview_sessions_failure_stage CHECK (
        (status = 'FAILED' AND failure_stage IN ('SCRIPT_GENERATION', 'NEXT_TURN', 'SCORING'))
        OR (status <> 'FAILED' AND failure_stage IS NULL)
    ),
    CONSTRAINT chk_interview_sessions_end_reason CHECK (
        end_reason IS NULL
        OR end_reason IN (
            'USER_COMPLETED', 'USER_COMPLETED_EARLY', 'USER_ABANDONED',
            'TIMEOUT_24H', 'SYSTEM_ERROR'
        )
    ),
    CONSTRAINT chk_interview_sessions_score CHECK (
        overall_score IS NULL OR overall_score BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_interview_sessions_completion CHECK (
        (status IN ('COMPLETED', 'ABANDONED') AND completed_at IS NOT NULL)
        OR (status NOT IN ('COMPLETED', 'ABANDONED') AND completed_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_interview_sessions_user_status_activity
    ON interview_sessions (user_id, status, last_activity_at);
CREATE INDEX idx_interview_sessions_user_completed
    ON interview_sessions (user_id, completed_at);
CREATE INDEX idx_interview_sessions_profile_id
    ON interview_sessions (profile_id);
CREATE INDEX idx_interview_sessions_job_description_id
    ON interview_sessions (job_description_id);
CREATE INDEX idx_interview_sessions_rubric_version_id
    ON interview_sessions (rubric_version_id);
CREATE INDEX idx_interview_sessions_recovery
    ON interview_sessions (status, processing_stage, next_retry_at);
CREATE INDEX idx_interview_sessions_last_activity
    ON interview_sessions (last_activity_at);

CREATE TABLE session_context_snapshots (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id              BIGINT NOT NULL,
    snapshot_schema_version VARCHAR(20) NOT NULL,
    profile_json            JSON NOT NULL,
    job_description_text    MEDIUMTEXT NOT NULL,
    job_description_hash    CHAR(64) NOT NULL,
    created_at              DATETIME(6) NOT NULL,

    CONSTRAINT uq_session_context_snapshots_session UNIQUE (session_id),
    CONSTRAINT fk_session_context_snapshots_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_session_context_snapshots_schema_version CHECK (
        CHAR_LENGTH(TRIM(snapshot_schema_version)) > 0
    ),
    CONSTRAINT chk_session_context_snapshots_profile_json CHECK (
        JSON_TYPE(profile_json) = 'OBJECT'
    ),
    CONSTRAINT chk_session_context_snapshots_jd_text CHECK (
        CHAR_LENGTH(TRIM(job_description_text)) > 0
    ),
    CONSTRAINT chk_session_context_snapshots_jd_hash CHECK (
        job_description_hash REGEXP '^[0-9a-f]{64}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE session_state_transitions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  BIGINT NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status   VARCHAR(30) NOT NULL,
    actor       VARCHAR(20) NOT NULL,
    reason      VARCHAR(255) NULL,
    occurred_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_session_state_transitions_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_session_state_transitions_from_status CHECK (
        from_status IS NULL
        OR from_status IN (
            'CREATED', 'SCRIPT_GENERATING', 'READY', 'IN_PROGRESS', 'PAUSED',
            'SCORING', 'COMPLETED', 'ABANDONED', 'FAILED'
        )
    ),
    CONSTRAINT chk_session_state_transitions_to_status CHECK (
        to_status IN (
            'CREATED', 'SCRIPT_GENERATING', 'READY', 'IN_PROGRESS', 'PAUSED',
            'SCORING', 'COMPLETED', 'ABANDONED', 'FAILED'
        )
    ),
    CONSTRAINT chk_session_state_transitions_actor CHECK (
        actor IN ('USER', 'SYSTEM', 'SCHEDULER')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_session_state_transitions_session_occurred
    ON session_state_transitions (session_id, occurred_at);

--rollback DROP TABLE session_state_transitions;
--rollback DROP TABLE session_context_snapshots;
--rollback DROP TABLE interview_sessions;
