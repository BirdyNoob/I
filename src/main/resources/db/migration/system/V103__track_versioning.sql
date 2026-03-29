ALTER TABLE system.tracks
    DROP CONSTRAINT IF EXISTS tracks_slug_key;

ALTER TABLE system.tracks
    ADD COLUMN IF NOT EXISTS previous_version_id UUID REFERENCES system.tracks(id),
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS change_summary TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_tracks_slug_version_unique
    ON system.tracks(slug, version);

UPDATE system.tracks
SET published_at = COALESCE(published_at, created_at)
WHERE is_published = TRUE
  AND published_at IS NULL;
