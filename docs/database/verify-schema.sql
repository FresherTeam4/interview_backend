-- Chạy sau khi ứng dụng/Liquibase khởi động thành công trên database đang chọn.

SELECT COUNT(*) AS expected_10_changesets
FROM databasechangelog
WHERE (author = 'tvt' AND id IN ('001-create-user-accounts', '002-create-refresh-tokens'))
   OR (author = 'nxt' AND id IN (
       '010-create-tech-stacks', '011-create-questions',
       '012-create-interview-sessions', '013-create-reports',
       '014-seed-sample-questions', '015-create-question-taxonomy',
       '016-migrate-question-taxonomy', '017-use-extensible-session-status'
   ));

SELECT COUNT(*) AS expected_10_tables_including_liquibase
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'user_accounts', 'refresh_tokens', 'tech_stacks', 'technologies',
      'questions', 'question_tech_stacks', 'question_technologies',
      'interview_sessions', 'reports', 'databasechangelog'
  );

SELECT COUNT(*) AS expected_6_tech_stacks FROM tech_stacks;
SELECT COUNT(*) AS expected_31_technologies FROM technologies;
SELECT COUNT(*) AS expected_32_questions FROM questions;

SELECT COUNT(*) AS expected_0_legacy_tech_stack_columns
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'questions'
  AND column_name = 'tech_stack_id';

SELECT COUNT(*) AS expected_1_extensible_session_status
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'interview_sessions'
  AND column_name = 'status'
  AND data_type = 'varchar'
  AND character_maximum_length = 30;

SELECT COUNT(*) AS expected_0_orphan_question_tech_stacks
FROM question_tech_stacks qts
LEFT JOIN questions q ON q.id = qts.question_id
LEFT JOIN tech_stacks ts ON ts.id = qts.tech_stack_id
WHERE q.id IS NULL OR ts.id IS NULL;

SELECT COUNT(*) AS expected_0_orphan_question_technologies
FROM question_technologies qt
LEFT JOIN questions q ON q.id = qt.question_id
LEFT JOIN technologies t ON t.id = qt.technology_id
WHERE q.id IS NULL OR t.id IS NULL;

SELECT COUNT(*) AS expected_0_unclassified_technical_questions
FROM (
    SELECT q.id
    FROM questions q
    LEFT JOIN question_tech_stacks qts ON qts.question_id = q.id
    WHERE q.question_type = 'TECHNICAL'
    GROUP BY q.id
    HAVING COUNT(qts.tech_stack_id) = 0
) unclassified_technical_questions;
