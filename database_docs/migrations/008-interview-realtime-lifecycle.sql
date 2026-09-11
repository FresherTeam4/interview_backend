ALTER TABLE interview_realtime_events
    ADD COLUMN detail_text MEDIUMTEXT NULL AFTER transcript_text,
    ADD COLUMN latency_ms INTEGER NULL AFTER detail_text,
    ADD CONSTRAINT chk_interview_realtime_event_latency CHECK (
        latency_ms IS NULL OR latency_ms >= 0);
