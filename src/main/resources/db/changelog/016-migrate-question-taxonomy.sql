--liquibase formatted sql
--changeset nxt:016-migrate-question-taxonomy labels:sprint-2
-- Preserve existing classifications, tag known seed questions, then remove the legacy single FK.

INSERT INTO question_tech_stacks (question_id, tech_stack_id)
SELECT id, tech_stack_id
FROM questions
WHERE tech_stack_id IS NOT NULL;

INSERT INTO question_technologies (question_id, technology_id)
SELECT q.id, t.id
FROM questions q
JOIN technologies t ON t.code = 'JAVASCRIPT'
WHERE q.content_en IN (
    'What is the difference between let, const, and var in JavaScript?',
    'Explain how the Virtual DOM works in React.',
    'How would you optimize the performance of a large-scale React application?'
);

INSERT INTO question_technologies (question_id, technology_id)
SELECT q.id, t.id
FROM questions q
JOIN technologies t ON t.code = 'REACT'
WHERE q.content_en IN (
    'Explain how the Virtual DOM works in React.',
    'How would you optimize the performance of a large-scale React application?'
);

INSERT INTO question_technologies (question_id, technology_id)
SELECT q.id, t.id
FROM questions q
JOIN technologies t ON t.code = 'CSS'
WHERE q.content_en = 'Compare CSS Grid and Flexbox — when should each be used?';

INSERT INTO question_technologies (question_id, technology_id)
SELECT q.id, t.id
FROM questions q
JOIN technologies t ON t.code = 'ANDROID'
WHERE q.content_en = 'Explain the lifecycle of an Activity in Android.';

INSERT INTO question_technologies (question_id, technology_id)
SELECT q.id, t.id
FROM questions q
JOIN technologies t ON t.code = 'DOCKER'
WHERE q.content_en = 'How does a Docker container differ from a Virtual Machine?';

DROP INDEX idx_questions_filter ON questions;
ALTER TABLE questions DROP FOREIGN KEY fk_questions_tech_stack;
ALTER TABLE questions DROP COLUMN tech_stack_id;

CREATE INDEX idx_questions_filter
    ON questions (is_active, level, question_type, difficulty);

--rollback DROP INDEX idx_questions_filter ON questions;
--rollback ALTER TABLE questions ADD COLUMN tech_stack_id INT UNSIGNED NULL AFTER content_en;
--rollback UPDATE questions q LEFT JOIN (SELECT question_id, MIN(tech_stack_id) AS tech_stack_id FROM question_tech_stacks GROUP BY question_id) qts ON qts.question_id = q.id SET q.tech_stack_id = qts.tech_stack_id;
--rollback ALTER TABLE questions ADD CONSTRAINT fk_questions_tech_stack FOREIGN KEY (tech_stack_id) REFERENCES tech_stacks (id) ON DELETE SET NULL ON UPDATE RESTRICT;
--rollback CREATE INDEX idx_questions_filter ON questions (is_active, tech_stack_id, level, question_type, difficulty);
