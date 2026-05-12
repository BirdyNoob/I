-- Add track_id to cheat_sheets so sheets can be scoped to a specific track.
-- NULL means global (visible to all learners regardless of track).
ALTER TABLE system.cheat_sheets
    ADD COLUMN IF NOT EXISTS track_id UUID;

-- Drop the old department index — filtering is now track-based.
-- We keep the department column for backward-compatibility but no longer use it for filtering.
CREATE INDEX IF NOT EXISTS idx_cheat_sheets_track_id ON system.cheat_sheets(track_id);
