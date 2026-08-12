--liquibase formatted sql
--changeset nxt:010-create-tech-stacks labels:sprint-1
-- MySQL 8.0.16+ / Liquibase
-- US-04: lookup table used to classify questions by technology group.

CREATE TABLE tech_stacks (
    id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name_vi     VARCHAR(100) NOT NULL,
    name_en     VARCHAR(100) NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                              ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_tech_stacks_code UNIQUE (code),
    CONSTRAINT chk_tech_stacks_code CHECK (code REGEXP '^[A-Z0-9_]+$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tech_stacks (code, name_vi, name_en) VALUES
    ('FRONTEND', 'Frontend',      'Frontend'),
    ('BACKEND',  'Backend',       'Backend'),
    ('MOBILE',   'Di động',       'Mobile'),
    ('DEVOPS',   'DevOps',        'DevOps'),
    ('DATA_AI',  'Dữ liệu & AI',  'Data & AI'),
    ('QA',       'Kiểm thử',       'QA / Testing');

--rollback DROP TABLE tech_stacks;
