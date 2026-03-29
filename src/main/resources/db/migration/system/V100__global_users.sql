-- ============================================================
-- V100: Global User Registry
-- Moves users out of tenant schemas into the system schema.
-- Also creates the tenant_users junction table for authorization.
-- ============================================================

-- Global users table (lives in the system schema alongside tenants)
CREATE TABLE IF NOT EXISTS system.users (
    id               UUID PRIMARY KEY,
    email            TEXT NOT NULL UNIQUE,
    name             TEXT,
    password_hash    TEXT NOT NULL,
    auth_provider    TEXT NOT NULL DEFAULT 'LOCAL',   -- LOCAL | GOOGLE | MICROSOFT
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at    TIMESTAMPTZ
);

-- Junction table: which users belong to which tenants, and with what role
CREATE TABLE IF NOT EXISTS system.tenant_users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES system.users(id) ON DELETE CASCADE,
    tenant_id   UUID NOT NULL REFERENCES system.tenants(id) ON DELETE CASCADE,
    role        TEXT NOT NULL,        -- LEARNER | ADMIN | SUPER_ADMIN
    department  TEXT,
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, tenant_id)      -- a user can only have one entry per tenant
);

-- Speed up "which tenants does this user belong to?" lookups
CREATE INDEX IF NOT EXISTS idx_tenant_users_user_id   ON system.tenant_users(user_id);
CREATE INDEX IF NOT EXISTS idx_tenant_users_tenant_id ON system.tenant_users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_email            ON system.users(email);
