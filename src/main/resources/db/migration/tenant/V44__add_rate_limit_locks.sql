CREATE TABLE rate_limit_locks (
    key_identifier VARCHAR(255) PRIMARY KEY,
    locked_until TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
