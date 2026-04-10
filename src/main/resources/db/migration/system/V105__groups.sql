CREATE TABLE IF NOT EXISTS system.user_groups (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES system.tenants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    CONSTRAINT uq_user_groups_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE IF NOT EXISTS system.group_memberships (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES system.user_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES system.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_group_memberships_group_user UNIQUE (group_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_groups_tenant ON system.user_groups(tenant_id);
CREATE INDEX IF NOT EXISTS idx_group_memberships_group ON system.group_memberships(group_id);
CREATE INDEX IF NOT EXISTS idx_group_memberships_user ON system.group_memberships(user_id);
