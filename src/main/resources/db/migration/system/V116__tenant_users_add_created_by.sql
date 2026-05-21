ALTER TABLE system.tenant_users ADD COLUMN created_by UUID REFERENCES system.users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_tenant_users_created_by ON system.tenant_users(created_by);
