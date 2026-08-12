-- Run with database selected via DB_URL as the active database.

SELECT COUNT(*) AS expected_7_changesets
FROM databasechangelog
WHERE (author = 'tvt' AND id IN ('001-create-user-accounts', '002-create-refresh-tokens'))
   OR (author = 'nxt' AND id IN (
       '010-create-tech-stacks', '011-create-questions',
       '012-create-interview-sessions', '013-create-reports',
       '014-seed-sample-questions'
   ));

SELECT COUNT(*) AS expected_7_application_tables
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'user_accounts', 'refresh_tokens', 'tech_stacks', 'questions',
      'interview_sessions', 'reports', 'databasechangelog'
  );

SELECT COUNT(*) AS expected_6_tech_stacks FROM tech_stacks;
SELECT COUNT(*) AS expected_32_questions FROM questions;

SELECT COUNT(*) AS expected_0_orphan_questions
FROM questions q
LEFT JOIN tech_stacks ts ON ts.id = q.tech_stack_id
WHERE q.tech_stack_id IS NOT NULL AND ts.id IS NULL;
