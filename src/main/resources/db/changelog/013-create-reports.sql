--liquibase formatted sql
--changeset nxt:013-create-reports labels:sprint-1
-- MySQL 8.0.16+ / Liquibase
-- US-04 schema core. Detailed analysis/report features are outside Sprint 1.

CREATE TABLE reports (
    id                        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    interview_session_id     BIGINT UNSIGNED NOT NULL,
    overall_score            DECIMAL(4,2) NULL COMMENT 'Scale 0.00 to 10.00',
    star_score_json           JSON NULL,
    communication_score_json JSON NULL,
    technical_score_json     JSON NULL,
    strengths                 TEXT NULL,
    weaknesses                TEXT NULL,
    drive_file_url            VARCHAR(1000) NULL,
    created_at                DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                          ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_reports_session UNIQUE (interview_session_id),
    CONSTRAINT fk_reports_session
        FOREIGN KEY (interview_session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_reports_overall_score CHECK (
        overall_score IS NULL OR overall_score BETWEEN 0.00 AND 10.00
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE reports;
