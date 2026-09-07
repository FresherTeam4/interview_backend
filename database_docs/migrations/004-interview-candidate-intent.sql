-- Hibernate ddl-auto can add a new enum column, but does not expand an existing enum CHECK constraint.
SET @current_schema = DATABASE();

SET @has_candidate_intent = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @current_schema
      AND TABLE_NAME = 'interview_turns'
      AND COLUMN_NAME = 'candidate_intent'
);
SET @migration_statement = IF(
    @has_candidate_intent = 0,
    'ALTER TABLE interview_turns ADD COLUMN candidate_intent VARCHAR(40) NULL AFTER content_text',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_statement;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @old_action_constraint = (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = @current_schema
      AND tc.TABLE_NAME = 'interview_turns'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND UPPER(cc.CHECK_CLAUSE) LIKE '%ACTION%IN (%'
      AND UPPER(cc.CHECK_CLAUSE) NOT LIKE '%HANDLE_REQUEST%'
    LIMIT 1
);
SET @migration_statement = IF(
    @old_action_constraint IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE interview_turns DROP CHECK `',
           REPLACE(@old_action_constraint, '`', '``'), '`')
);
PREPARE migration_statement FROM @migration_statement;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @has_current_action_constraint = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = @current_schema
      AND tc.TABLE_NAME = 'interview_turns'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND UPPER(cc.CHECK_CLAUSE) LIKE '%ACTION%IN (%'
      AND UPPER(cc.CHECK_CLAUSE) LIKE '%HANDLE_REQUEST%'
);
SET @migration_statement = IF(
    @has_current_action_constraint = 0,
    'ALTER TABLE interview_turns ADD CONSTRAINT chk_interview_turn_action CHECK (action IS NULL OR action IN (''OPENING'', ''EXPLORE'', ''FOLLOW_UP'', ''HANDLE_REQUEST'', ''CLOSE''))',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_statement;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @has_intent_constraint = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
     AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = @current_schema
      AND tc.TABLE_NAME = 'interview_turns'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND UPPER(cc.CHECK_CLAUSE) LIKE '%CANDIDATE_INTENT%IN (%'
);
SET @migration_statement = IF(
    @has_intent_constraint = 0,
    'ALTER TABLE interview_turns ADD CONSTRAINT chk_interview_turn_intent CHECK (candidate_intent IS NULL OR candidate_intent IN (''ANSWER'', ''REQUEST_REPEAT'', ''REQUEST_CLARIFICATION'', ''REQUEST_TIME'', ''ASK_INTERVIEWER'', ''CANNOT_ANSWER'', ''DECLINE_OR_SKIP'', ''CORRECT_PREVIOUS_ANSWER'', ''REQUEST_END'', ''SOCIAL_OR_META'', ''OFF_TOPIC'', ''INAPPROPRIATE'', ''OTHER''))',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_statement;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @current_schema = NULL;
SET @has_candidate_intent = NULL;
SET @old_action_constraint = NULL;
SET @has_current_action_constraint = NULL;
SET @has_intent_constraint = NULL;
SET @migration_statement = NULL;
