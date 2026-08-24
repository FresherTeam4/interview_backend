--liquibase formatted sql
--changeset team:019-drop-legacy-session-tables
--comment The question-bank feature (changesets 010-017) was removed from main, but databases migrated before that refactor still hold its interview_sessions/reports tables. The mock-interview schema below reclaims the interview_sessions name, so those two leftovers must go first. Both are empty by design (the feature never had services). Fresh databases: no-op.

DROP TABLE IF EXISTS reports;
DROP TABLE IF EXISTS interview_sessions;

--rollback SELECT 1;
