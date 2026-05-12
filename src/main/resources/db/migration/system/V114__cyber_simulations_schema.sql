-- Drop old simulation tables
DROP TABLE IF EXISTS system.simulation_choices CASCADE;
DROP TABLE IF EXISTS system.simulation_scenarios CASCADE;
DROP TABLE IF EXISTS system.simulations CASCADE;

-- Recreate system.simulations for the new text-based prompt
CREATE TABLE system.simulations (
    id UUID PRIMARY KEY,
    track_id UUID NOT NULL REFERENCES system.tracks(id),
    title TEXT NOT NULL,
    description TEXT,
    simulation_type VARCHAR(50) NOT NULL,
    difficulty_level VARCHAR(50) NOT NULL,
    scenario_prompt TEXT NOT NULL,
    estimated_mins INTEGER NOT NULL DEFAULT 0,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    config_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_simulations_track_id ON system.simulations(track_id);
CREATE INDEX idx_simulations_published ON system.simulations(published);

-- Create Evaluation Rules table
CREATE TABLE system.simulation_evaluation_rules (
    id UUID PRIMARY KEY,
    simulation_id UUID NOT NULL REFERENCES system.simulations(id) ON DELETE CASCADE,
    rule_type VARCHAR(50) NOT NULL,
    rule_pattern TEXT NOT NULL,
    penalty_points INTEGER NOT NULL DEFAULT 0,
    feedback_text TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_evaluation_rules_sim_id ON system.simulation_evaluation_rules(simulation_id);

-- Create Score Configurations
CREATE TABLE system.simulation_score_configs (
    id UUID PRIMARY KEY,
    simulation_id UUID NOT NULL REFERENCES system.simulations(id) ON DELETE CASCADE,
    base_score INTEGER NOT NULL DEFAULT 100,
    critical_threshold INTEGER NOT NULL DEFAULT 50,
    high_threshold INTEGER NOT NULL DEFAULT 70,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(simulation_id)
);
