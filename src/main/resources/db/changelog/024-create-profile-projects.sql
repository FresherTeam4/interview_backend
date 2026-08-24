--liquibase formatted sql
--changeset tvt:024-create-profile-projects labels:sprint-1
-- MySQL 8.0.16+ / Liquibase
-- Child of candidate_profiles. description is the main raw material for
-- deep-dive question generation, and session_questions.source_project_id
-- will reference this table.

CREATE TABLE profile_projects (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id      BIGINT NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT NULL COMMENT 'Main raw material for deep-dive question generation',
    role_in_project VARCHAR(150) NULL,
    tech_stack      TEXT NULL COMMENT 'Comma-separated list; a join table is not needed for the MVP',
    start_date      DATE NULL,
    end_date        DATE NULL,
    is_user_edited  BOOLEAN NOT NULL DEFAULT FALSE,
    display_order   SMALLINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_profile_projects_profile
        FOREIGN KEY (profile_id) REFERENCES candidate_profiles (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_profile_projects_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_profile_projects_dates CHECK (
        start_date IS NULL OR end_date IS NULL OR end_date >= start_date
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_profile_projects_profile_id ON profile_projects (profile_id);

--rollback DROP TABLE profile_projects;
