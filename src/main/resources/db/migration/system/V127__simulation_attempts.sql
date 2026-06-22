CREATE TABLE system.simulation_attempts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    sim_id          TEXT NOT NULL,
    tenant_slug     TEXT NOT NULL,
    answers         JSONB NOT NULL,
    score           INT NOT NULL,
    total_questions INT NOT NULL,
    percentage      INT NOT NULL,
    passed          BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sim_attempts_user ON system.simulation_attempts(user_id);
CREATE INDEX idx_sim_attempts_sim ON system.simulation_attempts(sim_id);
CREATE INDEX idx_sim_attempts_tenant ON system.simulation_attempts(tenant_slug);
