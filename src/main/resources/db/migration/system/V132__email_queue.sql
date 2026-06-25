CREATE TABLE system.email_queue (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient     TEXT NOT NULL,
    subject       TEXT NOT NULL,
    template_name TEXT NOT NULL,
    variables     JSONB NOT NULL DEFAULT '{}',
    status        TEXT NOT NULL DEFAULT 'PENDING',
    retry_count   INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at  TIMESTAMPTZ
);

CREATE INDEX idx_email_queue_status ON system.email_queue(status) WHERE status = 'PENDING';
