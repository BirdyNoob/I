CREATE TABLE system.simulations (
    id UUID PRIMARY KEY,
    track_id UUID NOT NULL REFERENCES system.tracks(id),
    title TEXT NOT NULL,
    description TEXT,
    simulation_type VARCHAR(50) NOT NULL,
    difficulty_level VARCHAR(50) NOT NULL,
    estimated_mins INTEGER NOT NULL DEFAULT 0,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    config_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_simulations_track_id ON system.simulations(track_id);
CREATE INDEX idx_simulations_published ON system.simulations(published);

CREATE TABLE system.simulation_scenarios (
    id UUID PRIMARY KEY,
    simulation_id UUID NOT NULL REFERENCES system.simulations(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    narrative TEXT,
    scene_assets JSONB,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (simulation_id, sort_order)
);

CREATE INDEX idx_simulation_scenarios_simulation_id ON system.simulation_scenarios(simulation_id);

CREATE TABLE system.simulation_choices (
    id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL REFERENCES system.simulation_scenarios(id) ON DELETE CASCADE,
    choice_text TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    correctness_score INTEGER NOT NULL DEFAULT 0,
    consequence_text TEXT,
    next_scenario_id UUID NULL REFERENCES system.simulation_scenarios(id),
    feedback_text TEXT,
    metadata_json JSONB,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (scenario_id, sort_order)
);

CREATE INDEX idx_simulation_choices_scenario_id ON system.simulation_choices(scenario_id);
