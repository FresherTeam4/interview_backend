--liquibase formatted sql
--changeset nxt:012-create-interview-sessions labels:sprint-1
-- MySQL 8.0.16+ / Liquibase
-- US-04 schema core. Detailed state-machine/round implementation belongs to later sprints.

CREATE TABLE interview_sessions (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT NOT NULL,
    target_position       VARCHAR(150) NULL,
    jd_text               MEDIUMTEXT NULL,
    cv_snapshot_url       VARCHAR(1000) NULL,
    status                ENUM('CREATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')
                              NOT NULL DEFAULT 'CREATED',
    current_round_order   SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    video_call_url        VARCHAR(1000) NULL,
    started_at            DATETIME(6) NULL,
    ended_at              DATETIME(6) NULL,
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_sessions_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_sessions_time CHECK (
        ended_at IS NULL OR (started_at IS NOT NULL AND ended_at >= started_at)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_sessions_user_created
    ON interview_sessions (user_id, created_at DESC);
CREATE INDEX idx_sessions_status ON interview_sessions (status);

--rollback DROP TABLE interview_sessions;
