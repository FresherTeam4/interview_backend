ALTER TABLE interview_sessions
    DROP CHECK chk_interview_session_mode;

UPDATE interview_sessions
SET mode = 'TURN_BASED'
WHERE mode IN ('TEXT', 'VOICE_TURN_BASED');

ALTER TABLE interview_sessions
    ALTER COLUMN mode SET DEFAULT 'TURN_BASED',
    ADD CONSTRAINT chk_interview_session_mode CHECK (
        mode IN ('TURN_BASED', 'VOICE_REALTIME'));

ALTER TABLE interview_turns
    DROP CHECK chk_interview_turn_input_mode;

UPDATE interview_turns
SET input_mode = 'VOICE'
WHERE input_mode = 'VOICE_TURN_BASED';

ALTER TABLE interview_turns
    ADD CONSTRAINT chk_interview_turn_input_mode CHECK (
        input_mode IN ('TEXT', 'VOICE', 'VOICE_REALTIME'));
