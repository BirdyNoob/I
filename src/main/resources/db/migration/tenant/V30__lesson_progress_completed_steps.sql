ALTER TABLE lesson_progress ADD COLUMN completed_step_ids JSONB DEFAULT '[]'::jsonb;
