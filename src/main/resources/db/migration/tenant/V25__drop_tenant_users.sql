-- ============================================================
-- V25: Remove tenant-local users table
-- Users now live globally in system.users.
-- References from other tenant tables (assignments, audit_logs, etc.)
-- will use application-level integrity against the global user ID.
-- ============================================================

-- Drop dependent indexes first
DROP INDEX IF EXISTS idx_users_email_lower;

-- Drop the old tenant-scoped users table
DROP TABLE IF EXISTS users CASCADE;
