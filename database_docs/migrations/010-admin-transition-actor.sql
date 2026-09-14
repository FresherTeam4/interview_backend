ALTER TABLE interview_session_transitions
    ADD CONSTRAINT chk_interview_session_transition_actor CHECK (
        actor IN ('USER', 'ADMIN', 'SYSTEM', 'SCHEDULER'));
