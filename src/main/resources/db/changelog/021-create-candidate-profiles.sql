--liquibase formatted sql
--changeset tvt:021-create-candidate-profiles labels:sprint-1
-- MySQL 8.0.16+ / Liquibase
-- The editable working copy of a parsed CV. cv_parse_results keeps the original AI
-- output, so "the edited version always wins" holds by design, with no priority flag.
-- One row per CV, not per user: a user keeps several CVs and picks which profile to
-- interview against, so uploading a new CV never touches an older profile's edits.

CREATE TABLE candidate_profiles (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL COMMENT 'Owner. A user may have many profiles, one per CV',
    cv_document_id   BIGINT NOT NULL COMMENT 'Which CV this profile was built from. UNIQUE: one profile per CV',
    headline         VARCHAR(255) NULL,
    years_experience DECIMAL(3,1) NULL,
    target_position  VARCHAR(150) NULL,
    seniority_level  VARCHAR(30) NULL COMMENT 'STUDENT | FRESHER | JUNIOR | MID | SENIOR',
    source           VARCHAR(20) NOT NULL DEFAULT 'AUTO_PARSED' COMMENT 'Application-managed ProfileSource',
    confirmed_at     DATETIME(6) NULL COMMENT 'NULL = the user has not pressed "Information is correct", which blocks starting a session',
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- Deliberately NO "ON UPDATE CURRENT_TIMESTAMP(6)": the application writes this column
    -- from its java.time.Clock bean, like every other timestamp in this table group.
    -- CURRENT_TIMESTAMP resolves in the MySQL session time zone (SYSTEM by default), while
    -- the app stores UTC Instants, so letting MySQL fill it puts one column of this table
    -- in local time and the rest in UTC -- a silent offset on any server that is not UTC.
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_candidate_profiles_cv_document UNIQUE (cv_document_id),
    CONSTRAINT fk_candidate_profiles_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    -- RESTRICT on purpose: a CV that still backs a profile must not be deleted.
    CONSTRAINT fk_candidate_profiles_cv_document
        FOREIGN KEY (cv_document_id) REFERENCES cv_documents (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_candidate_profiles_source CHECK (
        source IN ('AUTO_PARSED', 'USER_EDITED')
    ),
    CONSTRAINT chk_candidate_profiles_years_experience CHECK (
        years_experience IS NULL OR years_experience >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- uq_candidate_profiles_cv_document already backs fk_candidate_profiles_cv_document.
-- user_id needs its own index: it is no longer UNIQUE, and fk_candidate_profiles_user
-- must have an index to sit on.
CREATE INDEX idx_candidate_profiles_user_id ON candidate_profiles (user_id);

--rollback DROP TABLE candidate_profiles;
