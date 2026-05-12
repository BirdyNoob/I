-- Drop old simulation attempt tables
DROP TABLE IF EXISTS simulation_choice_logs CASCADE;
DROP TABLE IF EXISTS simulation_attempts CASCADE;

-- Create new text-based attempt table
CREATE TABLE simulation_attempts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    simulation_id UUID NOT NULL, -- references system.simulations
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    status VARCHAR(50) NOT NULL,
    user_response TEXT,
    final_score INTEGER,
    risk_category VARCHAR(50),
    triggered_rules JSONB,
    duration_seconds INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_simulation_attempts_user_id ON simulation_attempts(user_id);
CREATE INDEX idx_simulation_attempts_sim_id ON simulation_attempts(simulation_id);

-- Create rule violations table for analytics
CREATE TABLE simulation_rule_violations (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES simulation_attempts(id) ON DELETE CASCADE,
    simulation_id UUID NOT NULL,
    rule_id UUID NOT NULL, -- references system.simulation_evaluation_rules
    rule_type VARCHAR(50) NOT NULL,
    penalty_applied INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sim_rule_violations_attempt ON simulation_rule_violations(attempt_id);
CREATE INDEX idx_sim_rule_violations_rule ON simulation_rule_violations(rule_id);
