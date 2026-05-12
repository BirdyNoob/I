CREATE TABLE simulation_attempts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    simulation_id UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL,
    current_scenario_id UUID,
    path_taken JSONB NOT NULL DEFAULT '[]'::jsonb,
    total_score INTEGER,
    completion_percentage INTEGER NOT NULL DEFAULT 0,
    duration_seconds INTEGER,
    insights_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_simulation_attempts_user_id ON simulation_attempts(user_id);
CREATE INDEX idx_simulation_attempts_simulation_id ON simulation_attempts(simulation_id);
CREATE INDEX idx_simulation_attempts_status ON simulation_attempts(status);

CREATE TABLE simulation_choices_log (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES simulation_attempts(id) ON DELETE CASCADE,
    choice_id UUID NOT NULL,
    scenario_id UUID NOT NULL,
    chosen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    choice_score INTEGER,
    feedback_given TEXT
);

CREATE INDEX idx_simulation_choices_log_attempt_id ON simulation_choices_log(attempt_id);

ALTER TABLE lesson_progress
    ADD COLUMN IF NOT EXISTS simulation_scores JSONB,
    ADD COLUMN IF NOT EXISTS decision_history JSONB;
