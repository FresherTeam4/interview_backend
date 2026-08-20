--liquibase formatted sql
--changeset nxt:019-add-question-fingerprint labels:question-import
-- Exact duplicate protection after canonical whitespace/case normalization.

ALTER TABLE questions
    ADD COLUMN content_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER content_en;

UPDATE questions
SET content_fingerprint = SHA2(
        LOWER(REGEXP_REPLACE(TRIM(content_vi), '[[:space:]]+', ' ')),
        256
    );

ALTER TABLE questions
    MODIFY COLUMN content_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD CONSTRAINT uq_questions_content_fingerprint UNIQUE (content_fingerprint);

--rollback ALTER TABLE questions DROP INDEX uq_questions_content_fingerprint, DROP COLUMN content_fingerprint;
