--liquibase formatted sql
--changeset team:020-create-user-preferences labels:interview-core

CREATE TABLE user_preferences (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    preferred_mode     VARCHAR(20) NOT NULL DEFAULT 'VOICE_TURN_BASED',
    barge_in_enabled   BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Interrupting the interviewer can be switched off per user',
    tts_voice_code     VARCHAR(50) NULL COMMENT 'Provider-specific voice id; NULL = provider default',
    interview_language VARCHAR(10) NOT NULL DEFAULT 'vi' COMMENT 'vi | en',
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                     ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_user_preferences_user UNIQUE (user_id),
    CONSTRAINT fk_user_preferences_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_user_preferences_mode CHECK (
        preferred_mode IN ('TEXT', 'VOICE_TURN_BASED', 'VOICE_REALTIME')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE user_preferences;
