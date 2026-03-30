ALTER TABLE notification_events
    ADD COLUMN IF NOT EXISTS event_key TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_notification_events_event_key
    ON notification_events(event_key)
    WHERE event_key IS NOT NULL;
