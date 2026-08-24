--liquibase formatted sql
--changeset team:023-create-rubrics labels:interview-core

CREATE TABLE rubrics (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(50) NOT NULL COMMENT 'e.g. TECH_INTERVIEW_FRESHER',
    name        VARCHAR(150) NOT NULL,
    description TEXT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_rubrics_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE rubrics;

--changeset team:023-create-rubric-versions labels:interview-core
--comment interview_sessions points at rubric_version_id, never at rubric_id, so editing a rubric cannot change the reports of sessions already scored.

CREATE TABLE rubric_versions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    rubric_id    BIGINT NOT NULL,
    version_no   INT NOT NULL,
    is_current   BOOLEAN NOT NULL DEFAULT FALSE,
    published_at DATETIME(6) NULL COMMENT 'NULL = draft, cannot be attached to a session',
    change_note  TEXT NULL,
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_rubric_versions_rubric_version UNIQUE (rubric_id, version_no),
    CONSTRAINT fk_rubric_versions_rubric
        FOREIGN KEY (rubric_id) REFERENCES rubrics (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_rubric_versions_version_no CHECK (version_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rubric_versions_current ON rubric_versions (rubric_id, is_current);

--rollback DROP TABLE rubric_versions;

--changeset team:023-create-rubric-criteria labels:interview-core

CREATE TABLE rubric_criteria (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    rubric_version_id BIGINT NOT NULL,
    code              VARCHAR(50) NOT NULL COMMENT 'e.g. TECHNICAL_DEPTH, COMMUNICATION',
    name              VARCHAR(150) NOT NULL,
    description       TEXT NULL,
    weight            DECIMAL(4,3) NOT NULL DEFAULT 1.000 COMMENT 'Weight used when computing the overall score',
    max_score         INT NOT NULL DEFAULT 4,
    display_order     INT NOT NULL DEFAULT 0,

    CONSTRAINT uq_rubric_criteria_version_code UNIQUE (rubric_version_id, code),
    CONSTRAINT fk_rubric_criteria_version
        FOREIGN KEY (rubric_version_id) REFERENCES rubric_versions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_rubric_criteria_weight CHECK (weight > 0),
    CONSTRAINT chk_rubric_criteria_max_score CHECK (max_score > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE rubric_criteria;

--changeset team:023-create-rubric-criterion-levels labels:interview-core

CREATE TABLE rubric_criterion_levels (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    criterion_id BIGINT NOT NULL,
    level_no     INT NOT NULL COMMENT 'Four levels per criterion',
    label        VARCHAR(80) NOT NULL COMMENT 'e.g. Chua dat / Co ban / Tot / Xuat sac',
    descriptor   TEXT NOT NULL COMMENT 'Wording both the model and the user read to agree what this level means',
    score_value  DECIMAL(4,2) NOT NULL,

    CONSTRAINT uq_rubric_criterion_levels_level UNIQUE (criterion_id, level_no),
    CONSTRAINT fk_rubric_criterion_levels_criterion
        FOREIGN KEY (criterion_id) REFERENCES rubric_criteria (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_rubric_criterion_levels_level_no CHECK (level_no > 0),
    CONSTRAINT chk_rubric_criterion_levels_score CHECK (score_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE rubric_criterion_levels;
