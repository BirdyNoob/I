-- Pre-computed tenant stats for fast dashboard loading
CREATE TABLE system.tenant_stats_cache (
    tenant_slug         TEXT PRIMARY KEY,
    total_users         BIGINT NOT NULL DEFAULT 0,
    total_assignments   BIGINT NOT NULL DEFAULT 0,
    completed_assignments BIGINT NOT NULL DEFAULT 0,
    overdue_assignments BIGINT NOT NULL DEFAULT 0,
    completion_percent  INT NOT NULL DEFAULT 0,
    certs_issued        BIGINT NOT NULL DEFAULT 0,
    dept_completion     JSONB NOT NULL DEFAULT '{}',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
