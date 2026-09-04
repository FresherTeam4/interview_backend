--liquibase formatted sql
--changeset tvt:032-create-interview-report-tables labels:interview-engine-mvp
-- MySQL 8.0.16+ / Liquibase
-- M10 persists immutable rubric scores, transcript evidence and the generated report.

CREATE TABLE session_scores (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id     BIGINT NOT NULL,
    criterion_id   BIGINT NOT NULL,
    criterion_code VARCHAR(50) NOT NULL,
    criterion_name VARCHAR(150) NOT NULL,
    score          DECIMAL(4,2) NOT NULL,
    max_score      DECIMAL(4,2) NOT NULL,
    level_no       SMALLINT NOT NULL,
    comment        TEXT NOT NULL,
    model_name     VARCHAR(100) NOT NULL,
    scored_at      DATETIME(6) NOT NULL,

    CONSTRAINT uq_session_scores_session_criterion UNIQUE (session_id, criterion_id),
    CONSTRAINT fk_session_scores_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_session_scores_criterion
        FOREIGN KEY (criterion_id) REFERENCES rubric_criteria (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_session_scores_values CHECK (
        score >= 0 AND max_score > 0 AND score <= max_score
    ),
    CONSTRAINT chk_session_scores_level CHECK (level_no > 0),
    CONSTRAINT chk_session_scores_comment CHECK (CHAR_LENGTH(TRIM(comment)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_session_scores_criterion_id ON session_scores (criterion_id);

CREATE TABLE score_evidences (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_score_id BIGINT NOT NULL,
    turn_id          BIGINT NOT NULL,
    quote_text       TEXT NOT NULL,
    start_offset     INT NOT NULL,
    end_offset       INT NOT NULL,

    CONSTRAINT fk_score_evidences_score
        FOREIGN KEY (session_score_id) REFERENCES session_scores (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_score_evidences_turn
        FOREIGN KEY (turn_id) REFERENCES session_turns (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_score_evidences_quote CHECK (CHAR_LENGTH(quote_text) > 0),
    CONSTRAINT chk_score_evidences_offsets CHECK (
        start_offset >= 0 AND end_offset > start_offset
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_score_evidences_score_id ON score_evidences (session_score_id);
CREATE INDEX idx_score_evidences_turn_id ON score_evidences (turn_id);

CREATE TABLE session_reports (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id       BIGINT NOT NULL,
    result_status    VARCHAR(40) NOT NULL,
    overall_score    DECIMAL(5,2) NULL,
    is_partial       BOOLEAN NOT NULL,
    completion_ratio DECIMAL(5,4) NOT NULL,
    assessed_weight  DECIMAL(4,3) NOT NULL,
    summary_text     TEXT NOT NULL,
    disclaimer       TEXT NOT NULL,
    language_code    VARCHAR(10) NOT NULL,
    model_name       VARCHAR(100) NULL,
    prompt_version   VARCHAR(20) NULL,
    duration_ms      INT NULL,
    generated_at     DATETIME(6) NOT NULL,

    CONSTRAINT uq_session_reports_session UNIQUE (session_id),
    CONSTRAINT fk_session_reports_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_session_reports_result CHECK (
        result_status IN ('SCORED', 'INSUFFICIENT_EVIDENCE')
    ),
    CONSTRAINT chk_session_reports_score CHECK (
        overall_score IS NULL OR overall_score BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_session_reports_ratio CHECK (
        completion_ratio BETWEEN 0 AND 1
    ),
    CONSTRAINT chk_session_reports_weight CHECK (
        assessed_weight BETWEEN 0 AND 1
    ),
    CONSTRAINT chk_session_reports_summary CHECK (CHAR_LENGTH(TRIM(summary_text)) > 0),
    CONSTRAINT chk_session_reports_disclaimer CHECK (CHAR_LENGTH(TRIM(disclaimer)) > 0),
    CONSTRAINT chk_session_reports_metadata CHECK (
        (result_status = 'SCORED'
            AND overall_score IS NOT NULL
            AND assessed_weight > 0
            AND model_name IS NOT NULL
            AND prompt_version IS NOT NULL
            AND duration_ms IS NOT NULL
            AND duration_ms >= 0)
        OR (result_status = 'INSUFFICIENT_EVIDENCE'
            AND overall_score IS NULL
            AND assessed_weight = 0
            AND model_name IS NULL
            AND prompt_version IS NULL
            AND duration_ms IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE report_highlights (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id     BIGINT NOT NULL,
    type          VARCHAR(20) NOT NULL,
    content       TEXT NOT NULL,
    display_order SMALLINT NOT NULL,

    CONSTRAINT uq_report_highlights_type_order UNIQUE (report_id, type, display_order),
    CONSTRAINT fk_report_highlights_report
        FOREIGN KEY (report_id) REFERENCES session_reports (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_report_highlights_type CHECK (
        type IN ('STRENGTH', 'IMPROVEMENT', 'NEXT_ACTION')
    ),
    CONSTRAINT chk_report_highlights_content CHECK (CHAR_LENGTH(TRIM(content)) > 0),
    CONSTRAINT chk_report_highlights_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_report_highlights_report_type_order
    ON report_highlights (report_id, type, display_order);

--rollback DROP TABLE report_highlights;
--rollback DROP TABLE session_reports;
--rollback DROP TABLE score_evidences;
--rollback DROP TABLE session_scores;
