--liquibase formatted sql
--changeset team:022-create-candidate-profiles labels:interview-core
--comment This is the working copy the user edits. cv_parse_results.raw_json keeps the untouched AI output, so "the edited version always wins" is guaranteed by structure instead of by a priority flag.

CREATE TABLE candidate_profiles (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL COMMENT 'One working profile per user',
    cv_document_id   BIGINT NOT NULL COMMENT 'Which CV this profile was derived from',
    headline         VARCHAR(255) NULL,
    years_experience DECIMAL(3,1) NULL,
    target_position  VARCHAR(150) NULL,
    seniority_level  VARCHAR(20) NULL,
    source           VARCHAR(20) NOT NULL DEFAULT 'AUTO_PARSED',
    confirmed_at     DATETIME(6) NULL COMMENT 'A session may only start after the user confirms the profile. NULL = not confirmed yet',
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                   ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_candidate_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_candidate_profiles_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_candidate_profiles_cv_document
        FOREIGN KEY (cv_document_id) REFERENCES cv_documents (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_candidate_profiles_source CHECK (
        source IN ('AUTO_PARSED', 'USER_EDITED')
    ),
    CONSTRAINT chk_candidate_profiles_seniority CHECK (
        seniority_level IS NULL
        OR seniority_level IN ('STUDENT', 'FRESHER', 'JUNIOR', 'MID', 'SENIOR')
    ),
    CONSTRAINT chk_candidate_profiles_experience CHECK (
        years_experience IS NULL OR years_experience >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE candidate_profiles;

--changeset team:022-create-profile-educations labels:interview-core

CREATE TABLE profile_educations (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id     BIGINT NOT NULL,
    school         VARCHAR(255) NOT NULL,
    degree         VARCHAR(150) NULL,
    field_of_study VARCHAR(150) NULL,
    start_year     INT NULL,
    end_year       INT NULL,
    is_user_edited BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'TRUE = added or corrected by the user',
    display_order  INT NOT NULL DEFAULT 0,

    CONSTRAINT fk_profile_educations_profile
        FOREIGN KEY (profile_id) REFERENCES candidate_profiles (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_profile_educations_years CHECK (
        start_year IS NULL OR end_year IS NULL OR end_year >= start_year
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_profile_educations_profile ON profile_educations (profile_id, display_order);

--rollback DROP TABLE profile_educations;

--changeset team:022-create-profile-skills labels:interview-core

CREATE TABLE profile_skills (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id     BIGINT NOT NULL,
    name           VARCHAR(80) NOT NULL COMMENT 'Java, Spring Boot, React, PostgreSQL...',
    category       VARCHAR(20) NULL,
    is_user_edited BOOLEAN NOT NULL DEFAULT FALSE,
    display_order  INT NOT NULL DEFAULT 0,

    CONSTRAINT uq_profile_skills_profile_name UNIQUE (profile_id, name),
    CONSTRAINT fk_profile_skills_profile
        FOREIGN KEY (profile_id) REFERENCES candidate_profiles (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_profile_skills_category CHECK (
        category IS NULL
        OR category IN ('LANGUAGE', 'FRAMEWORK', 'DATABASE', 'TOOL', 'SOFT')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE profile_skills;

--changeset team:022-create-profile-projects labels:interview-core

CREATE TABLE profile_projects (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id      BIGINT NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT NULL COMMENT 'Main raw material for deep-dive questions',
    role_in_project VARCHAR(150) NULL,
    tech_stack      TEXT NULL COMMENT 'Comma separated; enough for the MVP, no join table yet',
    start_date      DATE NULL,
    end_date        DATE NULL,
    is_user_edited  BOOLEAN NOT NULL DEFAULT FALSE,
    display_order   INT NOT NULL DEFAULT 0,

    CONSTRAINT fk_profile_projects_profile
        FOREIGN KEY (profile_id) REFERENCES candidate_profiles (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_profile_projects_dates CHECK (
        start_date IS NULL OR end_date IS NULL OR end_date >= start_date
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_profile_projects_profile ON profile_projects (profile_id, display_order);

--rollback DROP TABLE profile_projects;
