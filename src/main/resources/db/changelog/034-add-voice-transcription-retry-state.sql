--liquibase formatted sql
--changeset tvt:034-add-voice-transcription-retry-state labels:interview-engine-mvp
-- MySQL 8.0.16+ / Liquibase
-- Durable retry state keeps STT bounded and recoverable across application restarts.

ALTER TABLE voice_answer_attempts
    ADD COLUMN processing_attempts SMALLINT NOT NULL DEFAULT 0 AFTER processing_started_at,
    ADD COLUMN next_retry_at DATETIME(6) NULL AFTER processing_attempts,
    ADD CONSTRAINT chk_voice_attempts_processing_attempts CHECK (processing_attempts >= 0);

CREATE INDEX idx_voice_attempts_retry
    ON voice_answer_attempts (status, next_retry_at, processing_started_at);

--rollback DROP INDEX idx_voice_attempts_retry ON voice_answer_attempts;
--rollback ALTER TABLE voice_answer_attempts DROP CHECK chk_voice_attempts_processing_attempts;
--rollback ALTER TABLE voice_answer_attempts DROP COLUMN next_retry_at, DROP COLUMN processing_attempts;
