CREATE TABLE IF NOT EXISTS system.assignment_notification_configs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL UNIQUE REFERENCES system.tenants(id) ON DELETE CASCADE,
    reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reminder_offsets_hours TEXT NOT NULL DEFAULT '48,24',
    escalation_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    escalation_delay_hours INT NOT NULL DEFAULT 24,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
