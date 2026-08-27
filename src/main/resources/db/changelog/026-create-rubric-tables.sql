--liquibase formatted sql
--changeset tvt:026-create-rubric-tables labels:interview-engine-mvp
-- MySQL 8.0.16+ / Liquibase
-- M03 stores immutable rubric versions. rubrics.current_version_id is the only current pointer.

CREATE TABLE rubrics (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    code               VARCHAR(50) NOT NULL,
    name               VARCHAR(150) NOT NULL,
    description        TEXT NULL,
    current_version_id BIGINT NULL,
    created_at         DATETIME(6) NOT NULL,

    CONSTRAINT uq_rubrics_code UNIQUE (code),
    CONSTRAINT chk_rubrics_code CHECK (CHAR_LENGTH(TRIM(code)) > 0),
    CONSTRAINT chk_rubrics_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rubrics_current_version_id ON rubrics (current_version_id);

CREATE TABLE rubric_versions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    rubric_id    BIGINT NOT NULL,
    version_no   INT NOT NULL,
    change_note  VARCHAR(500) NULL,
    published_at DATETIME(6) NULL,
    created_at   DATETIME(6) NOT NULL,

    CONSTRAINT uq_rubric_versions_rubric_version UNIQUE (rubric_id, version_no),
    CONSTRAINT fk_rubric_versions_rubric
        FOREIGN KEY (rubric_id) REFERENCES rubrics (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_rubric_versions_version_no CHECK (version_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rubric_criteria (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    rubric_version_id BIGINT NOT NULL,
    code              VARCHAR(50) NOT NULL,
    name              VARCHAR(150) NOT NULL,
    description       TEXT NOT NULL,
    weight            DECIMAL(4,3) NOT NULL,
    max_score         SMALLINT NOT NULL DEFAULT 4,
    display_order     SMALLINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_rubric_criteria_version_code UNIQUE (rubric_version_id, code),
    CONSTRAINT fk_rubric_criteria_version
        FOREIGN KEY (rubric_version_id) REFERENCES rubric_versions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_rubric_criteria_code CHECK (CHAR_LENGTH(TRIM(code)) > 0),
    CONSTRAINT chk_rubric_criteria_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_rubric_criteria_description CHECK (CHAR_LENGTH(TRIM(description)) > 0),
    CONSTRAINT chk_rubric_criteria_weight CHECK (weight > 0 AND weight <= 1),
    CONSTRAINT chk_rubric_criteria_max_score CHECK (max_score > 0),
    CONSTRAINT chk_rubric_criteria_display_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rubric_criteria_version_order
    ON rubric_criteria (rubric_version_id, display_order);

CREATE TABLE rubric_criterion_levels (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    criterion_id BIGINT NOT NULL,
    level_no     SMALLINT NOT NULL,
    label        VARCHAR(80) NOT NULL,
    descriptor   TEXT NOT NULL,
    score_value  DECIMAL(4,2) NOT NULL,

    CONSTRAINT uq_rubric_criterion_levels_criterion_level UNIQUE (criterion_id, level_no),
    CONSTRAINT fk_rubric_criterion_levels_criterion
        FOREIGN KEY (criterion_id) REFERENCES rubric_criteria (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_rubric_criterion_levels_level_no CHECK (level_no > 0),
    CONSTRAINT chk_rubric_criterion_levels_label CHECK (CHAR_LENGTH(TRIM(label)) > 0),
    CONSTRAINT chk_rubric_criterion_levels_descriptor CHECK (CHAR_LENGTH(TRIM(descriptor)) > 0),
    CONSTRAINT chk_rubric_criterion_levels_score_value CHECK (score_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE rubrics
    ADD CONSTRAINT fk_rubrics_current_version
        FOREIGN KEY (current_version_id) REFERENCES rubric_versions (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

--rollback DROP TABLE rubric_criterion_levels;
--rollback DROP TABLE rubric_criteria;
--rollback ALTER TABLE rubrics DROP FOREIGN KEY fk_rubrics_current_version;
--rollback DROP TABLE rubric_versions;
--rollback DROP TABLE rubrics;
