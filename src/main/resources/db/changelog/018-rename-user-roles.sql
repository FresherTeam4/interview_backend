--liquibase formatted sql
--changeset team:018-rename-user-roles

ALTER TABLE user_accounts
    DROP CHECK chk_user_accounts_role;

UPDATE user_accounts
SET role = CASE role
    WHEN 'EVENT_ADMIN' THEN 'ADMIN'
    WHEN 'PARTICIPANT' THEN 'USER'
    ELSE role
END
WHERE role IN ('EVENT_ADMIN', 'PARTICIPANT');

ALTER TABLE user_accounts
    MODIFY COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE user_accounts
    ADD CONSTRAINT chk_user_accounts_role
        CHECK (role IN ('ADMIN', 'USER'));

--rollback ALTER TABLE user_accounts DROP CHECK chk_user_accounts_role;
--rollback UPDATE user_accounts
--rollback SET role = CASE role
--rollback     WHEN 'ADMIN' THEN 'EVENT_ADMIN'
--rollback     WHEN 'USER' THEN 'PARTICIPANT'
--rollback     ELSE role
--rollback END
--rollback WHERE role IN ('ADMIN', 'USER');
--rollback ALTER TABLE user_accounts
--rollback     MODIFY COLUMN role VARCHAR(20) NOT NULL DEFAULT 'PARTICIPANT';
--rollback ALTER TABLE user_accounts
--rollback     ADD CONSTRAINT chk_user_accounts_role
--rollback         CHECK (role IN ('EVENT_ADMIN', 'PARTICIPANT'));
