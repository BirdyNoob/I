ALTER TABLE assessment_attempts ADD COLUMN saved_answers JSONB;
ALTER TABLE assessment_attempts ADD COLUMN time_remaining_seconds INTEGER;
ALTER TABLE assessment_attempts ADD COLUMN last_saved_at TIMESTAMP WITH TIME ZONE;
