--liquibase formatted sql
--changeset tvt:021-create-candidate-profiles labels:sprint-1
-- MySQL 8.0.16+ / Liquibase
-- The editable working copy of a parsed CV. cv_parse_results keeps the original AI
-- output, so "the edited version always wins" holds by design, with no priority flag.

CREATE TABLE candidate_profiles (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL COMMENT 'One live profile per user',
    cv_document_id   BIGINT NOT NULL COMMENT 'Which CV this profile was built from',
    headline         VARCHAR(255) NULL,
    years_experience DECIMAL(3,1) NULL,
    target_position  VARCHAR(150) NULL,
    seniority_level  VARCHAR(30) NULL COMMENT 'STUDENT | FRESHER | JUNIOR | MID | SENIOR',
    source           VARCHAR(20) NOT NULL DEFAULT 'AUTO_PARSED' COMMENT 'Application-managed ProfileSource',
    confirmed_at     DATETIME(6) NULL COMMENT 'NULL = the user has not pressed "Information is correct", which blocks starting a session',
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                 ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_candidate_profiles_user UNIQUE (user_id),
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

-- uq_candidate_profiles_user already backs fk_candidate_profiles_user.
CREATE INDEX idx_candidate_profiles_cv_document_id ON candidate_profiles (cv_document_id);

--rollback DROP TABLE candidate_profiles;
