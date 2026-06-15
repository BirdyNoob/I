-- NLP-Lite: multi-step scenario attempts (replaces single-response attempt model)

CREATE TABLE sim_scenario_attempts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    scenario_id     UUID NOT NULL,   -- references system.simulation_scenarios
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS, COMPLETED, ABANDONED
    current_step    INTEGER NOT NULL DEFAULT 0,
    final_score     INTEGER,
    max_score       INTEGER,
    risk_level      VARCHAR(20),     -- SAFE, MEDIUM, HIGH, CRITICAL
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sca_user ON sim_scenario_attempts(user_id);
CREATE INDEX idx_sca_scenario ON sim_scenario_attempts(scenario_id);

-- Per-step responses within an attempt
CREATE TABLE sim_step_attempts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id       UUID NOT NULL REFERENCES sim_scenario_attempts(id) ON DELETE CASCADE,
    step_id          UUID NOT NULL,   -- references system.simulation_steps
    step_order       INTEGER NOT NULL,
    response_text    TEXT,            -- free-text answer (null for MCQ)
    selected_options JSONB,           -- array of question IDs selected (for MCQ)
    detected_intents JSONB,           -- [{intentCode, intentType, points}]
    points_earned    INTEGER NOT NULL DEFAULT 0,
    feedback         TEXT,
    submitted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ssa_attempt ON sim_step_attempts(attempt_id);
