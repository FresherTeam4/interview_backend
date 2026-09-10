ALTER TABLE interview_assessments
    ADD COLUMN technical_feedback TEXT NULL AFTER overall_summary,
    ADD COLUMN recommendations_json JSON NULL AFTER technical_feedback;

UPDATE interview_assessments
SET overall_summary = LEFT(overall_summary, 400),
    technical_feedback = LEFT(overall_summary, 300),
    communication_feedback = LEFT(communication_feedback, 300),
    recommendations_json = COALESCE(
        (
            SELECT JSON_ARRAYAGG(LEFT(items.recommendation, 200))
            FROM JSON_TABLE(
                improvements_json,
                '$[*]' COLUMNS (
                    item_order FOR ORDINALITY,
                    recommendation VARCHAR(1000) PATH '$.description'
                )
            ) AS items
            WHERE items.item_order <= 3
              AND NULLIF(TRIM(items.recommendation), '') IS NOT NULL
        ),
        JSON_ARRAY());

ALTER TABLE interview_assessments
    MODIFY COLUMN technical_feedback TEXT NOT NULL,
    MODIFY COLUMN recommendations_json JSON NOT NULL;

ALTER TABLE interview_assessments
    DROP CHECK chk_interview_assessment_confidence;

ALTER TABLE interview_assessments
    DROP COLUMN confidence,
    DROP COLUMN strengths_json,
    DROP COLUMN improvements_json,
    DROP COLUMN action_plan_json;

ALTER TABLE interview_focus_area_results
    DROP CHECK chk_interview_focus_result_confidence,
    DROP CHECK chk_interview_focus_result_shape;

ALTER TABLE interview_focus_area_results
    DROP COLUMN confidence,
    DROP COLUMN rationale,
    DROP COLUMN strengths_json,
    DROP COLUMN gaps_json,
    DROP COLUMN feedback,
    ADD CONSTRAINT chk_interview_focus_result_shape CHECK (
        (evidence_status = 'NOT_EXPLORED' AND score IS NULL)
        OR
        (evidence_status IN ('PARTIAL', 'SUFFICIENT') AND score IS NOT NULL));
