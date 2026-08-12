--liquibase formatted sql
--changeset tvt:001-create-user-accounts labels:sprint-1

CREATE TABLE user_accounts (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name      VARCHAR(150) NOT NULL,
    email          VARCHAR(150) NOT NULL COMMENT 'Store normalized lowercase email',
    password_hash  VARCHAR(255) NULL COMMENT 'NULL for a Google-only account',
    google_id      VARCHAR(255) NULL COMMENT 'Google OpenID Connect sub claim',
    avatar_url     VARCHAR(1000) NULL,
    role           VARCHAR(20) NOT NULL DEFAULT 'PARTICIPANT',
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                 ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_user_accounts_email UNIQUE (email),
    CONSTRAINT uq_user_accounts_google_id UNIQUE (google_id),
    CONSTRAINT chk_user_accounts_login_method CHECK (
        password_hash IS NOT NULL OR google_id IS NOT NULL
    ),
    CONSTRAINT chk_user_accounts_role CHECK (
        role IN ('EVENT_ADMIN', 'PARTICIPANT')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_user_accounts_role_enabled ON user_accounts (role, enabled);

--rollback DROP TABLE user_accounts;
