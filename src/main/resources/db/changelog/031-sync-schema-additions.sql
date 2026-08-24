--liquibase formatted sql
--changeset team:031-add-email-verified-at labels:schema-sync
--comment Add email_verified_at column to user_accounts for email verification flow. NULL = not yet verified.

ALTER TABLE user_accounts
    ADD COLUMN email_verified_at DATETIME(6) NULL
        COMMENT 'NULL = email not yet verified' AFTER enabled;

--rollback ALTER TABLE user_accounts DROP COLUMN email_verified_at;

--changeset team:031-add-generation-seed labels:schema-sync
--comment Add generation_seed column to session_questions so each question tracks its own seed.

ALTER TABLE session_questions
    ADD COLUMN generation_seed VARCHAR(64) NULL
        COMMENT 'Seed for this question; re-running on the same CV must produce a different set' AFTER source_skill_id;

--rollback ALTER TABLE session_questions DROP COLUMN generation_seed;

--changeset team:031-widen-seniority-level labels:schema-sync
--comment Widen seniority_level from VARCHAR(20) to VARCHAR(30) to match DDL schema.

ALTER TABLE candidate_profiles
    MODIFY COLUMN seniority_level VARCHAR(30) NULL;

--rollback ALTER TABLE candidate_profiles MODIFY COLUMN seniority_level VARCHAR(20) NULL;

--changeset team:031-widen-skill-category labels:schema-sync
--comment Widen category from VARCHAR(20) to VARCHAR(50) to match DDL schema.

ALTER TABLE profile_skills
    MODIFY COLUMN category VARCHAR(50) NULL;

-- Drop the existing CHECK before re-adding with the wider column
ALTER TABLE profile_skills
    DROP CHECK chk_profile_skills_category;

ALTER TABLE profile_skills
    ADD CONSTRAINT chk_profile_skills_category CHECK (
        category IS NULL
        OR category IN ('LANGUAGE', 'FRAMEWORK', 'DATABASE', 'TOOL', 'SOFT')
    );

--rollback ALTER TABLE profile_skills MODIFY COLUMN category VARCHAR(20) NULL;
