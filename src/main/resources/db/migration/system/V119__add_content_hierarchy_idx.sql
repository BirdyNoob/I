-- V119: Add indexes to system.modules and system.lessons to optimize hierarchical track structure queries and prevent full table scans.
-- Lives inside the system schema.

CREATE INDEX IF NOT EXISTS idx_modules_track_id 
    ON system.modules (track_id);

CREATE INDEX IF NOT EXISTS idx_lessons_module_id 
    ON system.lessons (module_id);
