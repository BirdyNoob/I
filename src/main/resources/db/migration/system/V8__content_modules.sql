-- Modules live inside a Track (track → modules → lessons hierarchy).
-- Must exist before V9__content_lessons.sql which FKs into this table.
CREATE TABLE system.modules (
    id           UUID PRIMARY KEY,
    track_id     UUID          NOT NULL REFERENCES system.tracks(id) ON DELETE CASCADE,
    title        TEXT          NOT NULL,
    description  TEXT,
    sort_order   INT           DEFAULT 0,
    is_published BOOLEAN       DEFAULT FALSE,
    created_at   TIMESTAMPTZ   DEFAULT NOW()
);
