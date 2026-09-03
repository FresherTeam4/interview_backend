--liquibase formatted sql
--changeset codex:031-decouple-question-sources-from-live-profile labels:interview-engine-mvp
-- Source IDs identify objects inside the immutable session snapshot, not mutable profile rows.

ALTER TABLE session_questions
    DROP FOREIGN KEY fk_session_questions_project,
    DROP FOREIGN KEY fk_session_questions_skill;

ALTER TABLE session_questions
    RENAME COLUMN source_project_id TO source_project_snapshot_id,
    RENAME COLUMN source_skill_id TO source_skill_snapshot_id;

ALTER TABLE session_questions
    RENAME INDEX idx_session_questions_project_id
        TO idx_session_questions_project_snapshot_id,
    RENAME INDEX idx_session_questions_skill_id
        TO idx_session_questions_skill_snapshot_id;

-- Rollback restores live-profile foreign keys. Snapshot IDs whose live rows were deleted become
-- NULL because the old schema cannot represent those historical references.
--rollback ALTER TABLE session_questions RENAME INDEX idx_session_questions_project_snapshot_id TO idx_session_questions_project_id, RENAME INDEX idx_session_questions_skill_snapshot_id TO idx_session_questions_skill_id;
--rollback ALTER TABLE session_questions RENAME COLUMN source_project_snapshot_id TO source_project_id, RENAME COLUMN source_skill_snapshot_id TO source_skill_id;
--rollback UPDATE session_questions question LEFT JOIN profile_projects project ON project.id = question.source_project_id SET question.source_project_id = NULL WHERE question.source_project_id IS NOT NULL AND project.id IS NULL;
--rollback UPDATE session_questions question LEFT JOIN profile_skills skill ON skill.id = question.source_skill_id SET question.source_skill_id = NULL WHERE question.source_skill_id IS NOT NULL AND skill.id IS NULL;
--rollback ALTER TABLE session_questions ADD CONSTRAINT fk_session_questions_project FOREIGN KEY (source_project_id) REFERENCES profile_projects (id) ON DELETE SET NULL ON UPDATE RESTRICT, ADD CONSTRAINT fk_session_questions_skill FOREIGN KEY (source_skill_id) REFERENCES profile_skills (id) ON DELETE SET NULL ON UPDATE RESTRICT;
