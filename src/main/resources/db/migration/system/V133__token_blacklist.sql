-- Blacklisted JWT tokens (invalidated on logout)
CREATE TABLE system.token_blacklist (
    token_hash  TEXT PRIMARY KEY,
    expires_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_token_blacklist_expires ON system.token_blacklist(expires_at);
