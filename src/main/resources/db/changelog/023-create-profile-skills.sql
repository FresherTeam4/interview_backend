--liquibase formatted sql
--changeset tvt:023-create-profile-skills labels:sprint-1
-- MySQL 8.0.16+ / Liquibase
-- Child of candidate_profiles. session_questions.source_skill_id will point here,
-- which is why skills are rows and not a JSON blob.
-- utf8mb4_unicode_ci makes uq_profile_skills_profile_name case-insensitive, so
-- "React" and "react" collide. Still normalise before writing: "ReactJS" does not.

CREATE TABLE profile_skills (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id     BIGINT NOT NULL,
    name           VARCHAR(80) NOT NULL COMMENT 'Java, Spring Boot, React, PostgreSQL...',
    category       VARCHAR(50) NULL COMMENT 'LANGUAGE | FRAMEWORK | DATABASE | TOOL | SOFT',
    is_user_edited BOOLEAN NOT NULL DEFAULT FALSE,
    display_order  SMALLINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_profile_skills_profile_name UNIQUE (profile_id, name),
    CONSTRAINT fk_profile_skills_profile
        FOREIGN KEY (profile_id) REFERENCES candidate_profiles (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_profile_skills_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- uq_profile_skills_profile_name already backs fk_profile_skills_profile.

--rollback DROP TABLE profile_skills;
