ALTER TABLE interview_sessions
    ADD COLUMN scoring_started_at DATETIME(6) NULL AFTER ended_at,
    ADD COLUMN scoring_error_code VARCHAR(80) NULL AFTER scoring_started_at,
    ADD COLUMN scoring_error_message TEXT NULL AFTER scoring_error_code;

CREATE TABLE interview_assessments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    technical_score DECIMAL(5, 2) NULL,
    communication_score DECIMAL(5, 2) NULL,
    overall_score DECIMAL(5, 2) NULL,
    coverage_percentage DECIMAL(5, 2) NOT NULL,
    confidence VARCHAR(10) NOT NULL,
    overall_summary TEXT NOT NULL,
    strengths_json JSON NOT NULL,
    improvements_json JSON NOT NULL,
    action_plan_json JSON NOT NULL,
    communication_feedback TEXT NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_interview_assessment_session UNIQUE (session_id),
    CONSTRAINT fk_interview_assessment_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_interview_assessment_scores CHECK (
        (technical_score IS NULL OR technical_score BETWEEN 0 AND 100)
        AND (communication_score IS NULL OR communication_score BETWEEN 0 AND 100)
        AND (overall_score IS NULL OR overall_score BETWEEN 0 AND 100)
        AND coverage_percentage BETWEEN 0 AND 100),
    CONSTRAINT chk_interview_assessment_confidence CHECK (
        confidence IN ('LOW', 'MEDIUM', 'HIGH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE interview_focus_area_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assessment_id BIGINT NOT NULL,
    focus_area_id BIGINT NOT NULL,
    score DECIMAL(5, 2) NULL,
    confidence VARCHAR(10) NOT NULL,
    evidence_status VARCHAR(20) NOT NULL,
    rationale TEXT NOT NULL,
    strengths_json JSON NOT NULL,
    gaps_json JSON NOT NULL,
    feedback TEXT NOT NULL,
    evidence_turn_ids_json JSON NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_interview_focus_result_area UNIQUE (assessment_id, focus_area_id),
    CONSTRAINT fk_interview_focus_result_assessment
        FOREIGN KEY (assessment_id) REFERENCES interview_assessments (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_interview_focus_result_area
        FOREIGN KEY (focus_area_id) REFERENCES interview_focus_areas (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    KEY idx_interview_focus_result_assessment (assessment_id),
    CONSTRAINT chk_interview_focus_result_score CHECK (
        score IS NULL OR score BETWEEN 0 AND 100),
    CONSTRAINT chk_interview_focus_result_confidence CHECK (
        confidence IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_interview_focus_result_evidence CHECK (
        evidence_status IN ('NOT_EXPLORED', 'PARTIAL', 'SUFFICIENT')),
    CONSTRAINT chk_interview_focus_result_shape CHECK (
        (evidence_status = 'NOT_EXPLORED' AND score IS NULL AND confidence = 'LOW')
        OR
        (evidence_status = 'PARTIAL' AND score IS NOT NULL AND confidence IN ('LOW', 'MEDIUM'))
        OR
        (evidence_status = 'SUFFICIENT' AND score IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
