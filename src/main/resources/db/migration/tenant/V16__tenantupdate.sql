ALTER TABLE quiz_attempts
    ADD COLUMN passed BOOLEAN;

ALTER TABLE quiz_attempts
    ADD COLUMN attempt_number INT;