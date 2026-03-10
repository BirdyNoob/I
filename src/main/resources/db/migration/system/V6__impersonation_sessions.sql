CREATE TABLE system.impersonation_sessions (
    id UUID PRIMARY KEY,
    platform_admin_id UUID,
    impersonated_user_id UUID,
    tenant_slug TEXT,
    reason TEXT,
    started_at TIMESTAMPTZ DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    actions_taken INT DEFAULT 0
);