-- Add version_label column for calendar-based versioning (e.g. "2026.6")
ALTER TABLE system.tracks ADD COLUMN version_label TEXT;
