--liquibase formatted sql
--changeset tvt:022-create-profile-educations labels:sprint-1
-- MySQL 8.0.16+ / Liquibase
-- Child of candidate_profiles. Rows are edited directly by the user.

CREATE TABLE profile_educations (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id     BIGINT NOT NULL,
    school         VARCHAR(255) NOT NULL,
    degree         VARCHAR(150) NULL,
    field_of_study VARCHAR(150) NULL,
    start_year     SMALLINT NULL,
    end_year       SMALLINT NULL,
    is_user_edited BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'true = the user corrected or added this row by hand; feeds parse-quality stats',
    display_order  SMALLINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_profile_educations_profile
        FOREIGN KEY (profile_id) REFERENCES candidate_profiles (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_profile_educations_school CHECK (CHAR_LENGTH(TRIM(school)) > 0),
    CONSTRAINT chk_profile_educations_years CHECK (
        (start_year IS NULL OR start_year BETWEEN 1900 AND 2100)
        AND (end_year IS NULL OR end_year BETWEEN 1900 AND 2100)
        AND (start_year IS NULL OR end_year IS NULL OR end_year >= start_year)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_profile_educations_profile_id ON profile_educations (profile_id);

--rollback DROP TABLE profile_educations;
