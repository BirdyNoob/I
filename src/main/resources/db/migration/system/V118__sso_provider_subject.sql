-- ============================================================
-- V118: SSO Provider Subject Tracking
-- Adds a stable provider identity column so returning SSO users
-- can be matched by their immutable provider subject ID (not email).
-- ============================================================

-- provider_subject: the "sub" claim (Google) or "oid" claim (Microsoft)
-- Null for LOCAL accounts. Set on first SSO login.
ALTER TABLE system.users
    ADD COLUMN IF NOT EXISTS provider_subject TEXT;

-- One provider+subject combo maps to exactly one Icentric user.
-- Partial index — only enforced when provider_subject is not null.
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_provider_subject
    ON system.users (auth_provider, provider_subject)
    WHERE provider_subject IS NOT NULL;
