-- V43: Add indexes to lesson_progress table to speed up user progress queries and prevent sequential full table scans.
-- Lives inside the tenant schema.

CREATE UNIQUE INDEX IF NOT EXISTS uk_lesson_progress_user_lesson 
    ON lesson_progress (user_id, lesson_id);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_user 
    ON lesson_progress (user_id);
