--liquibase formatted sql
--changeset team:030-seed-filler-words labels:interview-core
--comment Deliberately conservative: only sounds/phrases that carry no meaning of their own. Real words that merely appear often in speech (thì, cái, là, actually, ...) are left out because counting them would inflate filler_count and make the metric useless. Note the utf8mb4_unicode_ci collation is accent- and case-insensitive, so lookups also match unaccented transcripts.

INSERT INTO filler_word_dictionary (language_code, word, is_active) VALUES
    ('vi', 'à', TRUE),
    ('vi', 'ừ', TRUE),
    ('vi', 'ờ', TRUE),
    ('vi', 'ừm', TRUE),
    ('vi', 'hmm', TRUE),
    ('vi', 'à thì', TRUE),
    ('vi', 'ừ thì', TRUE),
    ('vi', 'kiểu như', TRUE),
    ('vi', 'nói chung là', TRUE),
    ('vi', 'đại khái là', TRUE),
    ('en', 'um', TRUE),
    ('en', 'uh', TRUE),
    ('en', 'er', TRUE),
    ('en', 'ah', TRUE),
    ('en', 'hmm', TRUE),
    ('en', 'like', TRUE),
    ('en', 'you know', TRUE),
    ('en', 'i mean', TRUE),
    ('en', 'sort of', TRUE),
    ('en', 'kind of', TRUE),
    ('en', 'basically', TRUE);

--rollback DELETE FROM filler_word_dictionary WHERE language_code IN ('vi', 'en');
