CREATE SCHEMA IF NOT EXISTS system;

CREATE TABLE IF NOT EXISTS system.tenants (
    id UUID PRIMARY KEY,
    slug TEXT NOT NULL UNIQUE,
    company_name TEXT,
    plan TEXT,
    max_seats INTEGER,
    status TEXT,
    created_at TIMESTAMPTZ,
    trial_ends_at TIMESTAMPTZ
);
