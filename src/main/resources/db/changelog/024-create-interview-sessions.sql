--liquibase formatted sql
--changeset team:024-create-interview-sessions labels:interview-core

CREATE TABLE interview_sessions (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    profile_id         BIGINT NOT NULL COMMENT 'Profile the questions were generated from at start time',
    rubric_version_id  BIGINT NOT NULL COMMENT 'Rubric version pinned when the session is created',
    mode               VARCHAR(20) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    current_turn_index INT NOT NULL DEFAULT 0 COMMENT 'Resume point when the session is reopened',
    script_seed        VARCHAR(64) NULL COMMENT 'Seed of the question-generation run; re-running on the same CV must produce a different script',
    end_reason         VARCHAR(20) NULL,
    started_at         DATETIME(6) NULL,
    last_activity_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Cleanup job: idle for more than 24h becomes EXPIRED and the answered part is still scored',
    completed_at       DATETIME(6) NULL,
    overall_score      DECIMAL(5,2) NULL COMMENT 'Copied from session_reports once the report exists, so the progress chart stays a single-table read',
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_interview_sessions_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_interview_sessions_profile
        FOREIGN KEY (profile_id) REFERENCES candidate_profiles (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_interview_sessions_rubric_version
        FOREIGN KEY (rubric_version_id) REFERENCES rubric_versions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_interview_sessions_mode CHECK (
        mode IN ('TEXT', 'VOICE_TURN_BASED', 'VOICE_REALTIME')
    ),
    CONSTRAINT chk_interview_sessions_status CHECK (
        status IN ('CREATED', 'SCRIPT_GENERATING', 'IN_PROGRESS', 'PAUSED',
                   'SCORING', 'COMPLETED', 'EXPIRED', 'ABANDONED')
    ),
    CONSTRAINT chk_interview_sessions_end_reason CHECK (
        end_reason IS NULL
        OR end_reason IN ('USER_COMPLETED', 'USER_ABANDONED', 'TIMEOUT_24H', 'SYSTEM_ERROR')
    ),
    CONSTRAINT chk_interview_sessions_turn_index CHECK (current_turn_index >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_interview_sessions_user_status ON interview_sessions (user_id, status);
CREATE INDEX idx_interview_sessions_user_completed ON interview_sessions (user_id, completed_at);
CREATE INDEX idx_interview_sessions_last_activity ON interview_sessions (last_activity_at);

--rollback DROP TABLE interview_sessions;

--changeset team:024-create-session-state-transitions labels:interview-core
--comment Append-only audit log: every status change is written here and never updated.

CREATE TABLE session_state_transitions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  BIGINT NOT NULL,
    from_status VARCHAR(20) NULL COMMENT 'NULL on the very first transition',
    to_status   VARCHAR(20) NOT NULL,
    reason      VARCHAR(255) NULL,
    actor       VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_session_state_transitions_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_session_state_transitions_from CHECK (
        from_status IS NULL
        OR from_status IN ('CREATED', 'SCRIPT_GENERATING', 'IN_PROGRESS', 'PAUSED',
                           'SCORING', 'COMPLETED', 'EXPIRED', 'ABANDONED')
    ),
    CONSTRAINT chk_session_state_transitions_to CHECK (
        to_status IN ('CREATED', 'SCRIPT_GENERATING', 'IN_PROGRESS', 'PAUSED',
                      'SCORING', 'COMPLETED', 'EXPIRED', 'ABANDONED')
    ),
    CONSTRAINT chk_session_state_transitions_actor CHECK (
        actor IN ('USER', 'SYSTEM', 'SCHEDULER')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_session_state_transitions_session ON session_state_transitions (session_id, occurred_at);

--rollback DROP TABLE session_state_transitions;

--changeset team:024-create-session-questions labels:interview-core
--comment The script (5-7 questions) is generated before the session starts. Follow-up questions asked during the conversation are NOT here - they are session_turns rows with is_followup = TRUE.

CREATE TABLE session_questions (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id        BIGINT NOT NULL,
    ordinal           INT NOT NULL COMMENT '1..7, position in the script',
    question_text     TEXT NOT NULL,
    topic             VARCHAR(150) NULL,
    difficulty        INT NULL COMMENT '1-5',
    source_project_id BIGINT NULL COMMENT 'CV project this question digs into; NULL for a fundamentals question',
    source_skill_id   BIGINT NULL COMMENT 'CV skill this question is about',
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_session_questions_ordinal UNIQUE (session_id, ordinal),
    CONSTRAINT fk_session_questions_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_session_questions_source_project
        FOREIGN KEY (source_project_id) REFERENCES profile_projects (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_session_questions_source_skill
        FOREIGN KEY (source_skill_id) REFERENCES profile_skills (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_session_questions_ordinal CHECK (ordinal > 0),
    CONSTRAINT chk_session_questions_difficulty CHECK (
        difficulty IS NULL OR difficulty BETWEEN 1 AND 5
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE session_questions;
