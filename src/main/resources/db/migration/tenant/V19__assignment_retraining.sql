ALTER TABLE user_assignments
    ADD COLUMN IF NOT EXISTS requires_retraining BOOLEAN DEFAULT FALSE;