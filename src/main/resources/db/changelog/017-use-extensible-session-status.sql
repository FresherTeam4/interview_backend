--liquibase formatted sql
--changeset nxt:017-use-extensible-session-status labels:sprint-1
-- US-19 will extend the interview state machine. VARCHAR avoids a table alteration
-- every time a new application state is introduced.

ALTER TABLE interview_sessions
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'CREATED'
    COMMENT 'Application-managed InterviewSessionStatus';

--rollback UPDATE interview_sessions SET status = 'CANCELLED' WHERE status NOT IN ('CREATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');
--rollback ALTER TABLE interview_sessions MODIFY COLUMN status ENUM('CREATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'CREATED';
