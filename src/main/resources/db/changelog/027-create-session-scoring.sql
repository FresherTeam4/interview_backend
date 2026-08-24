--liquibase formatted sql
--changeset team:027-create-session-scores labels:interview-core

CREATE TABLE session_scores (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id   BIGINT NOT NULL,
    criterion_id BIGINT NOT NULL,
    score        DECIMAL(4,2) NOT NULL,
    max_score    DECIMAL(4,2) NOT NULL COMMENT 'Snapshot of the criterion ceiling so a report renders without re-reading the rubric',
    level_no     INT NULL COMMENT 'Which of the criterion levels the answer landed on',
    comment_text TEXT NOT NULL COMMENT 'Every criterion must be explainable - no silent zero',
    scored_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    model_name   VARCHAR(100) NULL,

    CONSTRAINT uq_session_scores_session_criterion UNIQUE (session_id, criterion_id),
    CONSTRAINT fk_session_scores_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_session_scores_criterion
        FOREIGN KEY (criterion_id) REFERENCES rubric_criteria (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_session_scores_range CHECK (score >= 0 AND score <= max_score),
    CONSTRAINT chk_session_scores_max CHECK (max_score > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE session_scores;

--changeset team:027-create-score-evidences labels:interview-core
--comment Without this table the "score comes with evidence" requirement is only a promise inside a prompt.

CREATE TABLE score_evidences (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_score_id BIGINT NOT NULL,
    turn_id          BIGINT NOT NULL COMMENT 'Which spoken turn the evidence comes from',
    quote_text       TEXT NOT NULL COMMENT 'Transcript quote used as proof',
    start_offset     INT NULL COMMENT 'Character offset inside the transcript, for UI highlighting',
    end_offset       INT NULL,

    CONSTRAINT fk_score_evidences_score
        FOREIGN KEY (session_score_id) REFERENCES session_scores (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_score_evidences_turn
        FOREIGN KEY (turn_id) REFERENCES session_turns (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_score_evidences_offsets CHECK (
        start_offset IS NULL OR end_offset IS NULL OR end_offset >= start_offset
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_score_evidences_score ON score_evidences (session_score_id);

--rollback DROP TABLE score_evidences;

--changeset team:027-create-session-reports labels:interview-core

CREATE TABLE session_reports (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id    BIGINT NOT NULL,
    overall_score DECIMAL(5,2) NOT NULL,
    summary_text  TEXT NOT NULL,
    disclaimer    TEXT NOT NULL COMMENT 'Must state this is a practice tool, not a competency certificate',
    language_code VARCHAR(10) NOT NULL DEFAULT 'vi',
    model_name    VARCHAR(100) NOT NULL,
    duration_ms   INT NULL COMMENT 'Target: generated in under 60 seconds',
    generated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_session_reports_session UNIQUE (session_id),
    CONSTRAINT fk_session_reports_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_session_reports_overall_score CHECK (overall_score >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE session_reports;

--changeset team:027-create-report-highlights labels:interview-core
--comment 3 strengths + 3 improvements + next actions. Separate rows instead of one stuffed text column.

CREATE TABLE report_highlights (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id     BIGINT NOT NULL,
    type          VARCHAR(20) NOT NULL,
    content       TEXT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,

    CONSTRAINT fk_report_highlights_report
        FOREIGN KEY (report_id) REFERENCES session_reports (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_report_highlights_type CHECK (
        type IN ('STRENGTH', 'IMPROVEMENT', 'NEXT_ACTION')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_report_highlights_report_type ON report_highlights (report_id, type);

--rollback DROP TABLE report_highlights;
