--liquibase formatted sql
--changeset nxt:015-create-question-taxonomy labels:sprint-2
-- Multi-dimensional taxonomy for Question Bank (US-11).

CREATE TABLE technologies (
    id               INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL,
    name_vi          VARCHAR(100) NOT NULL,
    name_en          VARCHAR(100) NOT NULL,
    technology_type  VARCHAR(20)  NOT NULL,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                  ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_technologies_code UNIQUE (code),
    CONSTRAINT chk_technologies_code CHECK (code REGEXP '^[A-Z0-9_]+$'),
    CONSTRAINT chk_technologies_type CHECK (
        technology_type IN ('LANGUAGE', 'FRAMEWORK', 'DATABASE', 'CLOUD', 'PLATFORM', 'TOOL')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_technologies_type_active
    ON technologies (technology_type, is_active, name_en);

CREATE TABLE question_tech_stacks (
    question_id   BIGINT UNSIGNED NOT NULL,
    tech_stack_id INT UNSIGNED    NOT NULL,

    PRIMARY KEY (question_id, tech_stack_id),
    CONSTRAINT fk_question_tech_stacks_question
        FOREIGN KEY (question_id) REFERENCES questions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_question_tech_stacks_tech_stack
        FOREIGN KEY (tech_stack_id) REFERENCES tech_stacks (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_question_tech_stacks_stack_question
    ON question_tech_stacks (tech_stack_id, question_id);

CREATE TABLE question_technologies (
    question_id  BIGINT UNSIGNED NOT NULL,
    technology_id INT UNSIGNED   NOT NULL,

    PRIMARY KEY (question_id, technology_id),
    CONSTRAINT fk_question_technologies_question
        FOREIGN KEY (question_id) REFERENCES questions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_question_technologies_technology
        FOREIGN KEY (technology_id) REFERENCES technologies (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_question_technologies_technology_question
    ON question_technologies (technology_id, question_id);

INSERT INTO technologies (code, name_vi, name_en, technology_type) VALUES
    ('JAVA',           'Java',           'Java',           'LANGUAGE'),
    ('CSHARP',         'C#',             'C#',             'LANGUAGE'),
    ('PYTHON',         'Python',         'Python',         'LANGUAGE'),
    ('JAVASCRIPT',     'JavaScript',     'JavaScript',     'LANGUAGE'),
    ('TYPESCRIPT',     'TypeScript',     'TypeScript',     'LANGUAGE'),
    ('GO',             'Go',             'Go',             'LANGUAGE'),
    ('PHP',            'PHP',            'PHP',            'LANGUAGE'),
    ('KOTLIN',         'Kotlin',         'Kotlin',         'LANGUAGE'),
    ('DART',           'Dart',           'Dart',           'LANGUAGE'),
    ('SQL',            'SQL',            'SQL',            'LANGUAGE'),
    ('CSS',            'CSS',            'CSS',            'LANGUAGE'),
    ('SPRING_BOOT',    'Spring Boot',    'Spring Boot',    'FRAMEWORK'),
    ('DOTNET',         '.NET',           '.NET',           'FRAMEWORK'),
    ('REACT',          'React',          'React',          'FRAMEWORK'),
    ('ANGULAR',        'Angular',        'Angular',        'FRAMEWORK'),
    ('VUE',            'Vue.js',         'Vue.js',         'FRAMEWORK'),
    ('FLUTTER',        'Flutter',        'Flutter',        'FRAMEWORK'),
    ('MYSQL',          'MySQL',          'MySQL',          'DATABASE'),
    ('POSTGRESQL',     'PostgreSQL',     'PostgreSQL',     'DATABASE'),
    ('MONGODB',        'MongoDB',        'MongoDB',        'DATABASE'),
    ('REDIS',          'Redis',          'Redis',          'DATABASE'),
    ('AWS',            'AWS',            'AWS',            'CLOUD'),
    ('AZURE',          'Azure',          'Azure',          'CLOUD'),
    ('GCP',            'Google Cloud',   'Google Cloud',   'CLOUD'),
    ('NODEJS',         'Node.js',        'Node.js',        'PLATFORM'),
    ('ANDROID',        'Android',        'Android',        'PLATFORM'),
    ('IOS',            'iOS',            'iOS',            'PLATFORM'),
    ('DOCKER',         'Docker',         'Docker',         'TOOL'),
    ('KUBERNETES',     'Kubernetes',     'Kubernetes',     'TOOL'),
    ('JENKINS',        'Jenkins',        'Jenkins',        'TOOL'),
    ('GITHUB_ACTIONS', 'GitHub Actions', 'GitHub Actions', 'TOOL');

--rollback DROP TABLE question_technologies;
--rollback DROP TABLE question_tech_stacks;
--rollback DROP TABLE technologies;
