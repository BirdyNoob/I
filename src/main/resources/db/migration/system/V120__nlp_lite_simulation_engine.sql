-- NLP-Lite Simulation Engine: Scenarios, Steps, Questions, Intents, Keywords

-- Scenarios (replaces flat simulations table for new engine; old table kept for backward compat)
CREATE TABLE system.simulation_scenarios (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    simulation_id UUID NOT NULL REFERENCES system.simulations(id) ON DELETE CASCADE,
    title         TEXT NOT NULL,
    description   TEXT,
    domain        VARCHAR(50) NOT NULL, -- DATA_LEAKAGE, PROMPT_INJECTION, etc.
    difficulty    VARCHAR(20) NOT NULL DEFAULT 'INTERMEDIATE', -- BEGINNER, INTERMEDIATE, ADVANCED
    sort_order    INTEGER NOT NULL DEFAULT 0,
    published     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sim_scenarios_sim_id ON system.simulation_scenarios(simulation_id);

-- Steps within a scenario (Information, MCQ, FreeText, Decision, Feedback)
CREATE TABLE system.simulation_steps (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scenario_id  UUID NOT NULL REFERENCES system.simulation_scenarios(id) ON DELETE CASCADE,
    step_type    VARCHAR(30) NOT NULL, -- INFORMATION, MCQ, FREE_TEXT, DECISION, FEEDBACK
    title        TEXT NOT NULL,
    content      TEXT NOT NULL,         -- narrative / question text shown to learner
    context_json JSONB,                 -- optional artifact: email, doc, chat snippet
    sort_order   INTEGER NOT NULL DEFAULT 0,
    is_mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sim_steps_scenario ON system.simulation_steps(scenario_id);

-- MCQ options (attached to steps of type MCQ)
CREATE TABLE system.simulation_questions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    step_id         UUID NOT NULL REFERENCES system.simulation_steps(id) ON DELETE CASCADE,
    question_type   VARCHAR(20) NOT NULL DEFAULT 'SINGLE_SELECT', -- SINGLE_SELECT, MULTI_SELECT
    option_text     TEXT NOT NULL,
    is_correct      BOOLEAN NOT NULL DEFAULT FALSE,
    points          INTEGER NOT NULL DEFAULT 0,
    feedback_text   TEXT,
    sort_order      INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_sim_questions_step ON system.simulation_questions(step_id);

-- Security intents (positive and negative)
CREATE TABLE system.security_intents (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intent_code   VARCHAR(100) NOT NULL UNIQUE,  -- e.g. VERIFY_SENDER, UPLOAD_TO_AI
    description   TEXT,
    intent_type   VARCHAR(10) NOT NULL DEFAULT 'POSITIVE', -- POSITIVE, NEGATIVE
    base_points   INTEGER NOT NULL DEFAULT 0,    -- +points for positive, -points for negative
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Keywords mapped to intents
CREATE TABLE system.intent_keywords (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intent_id  UUID NOT NULL REFERENCES system.security_intents(id) ON DELETE CASCADE,
    keyword    TEXT NOT NULL,
    match_type VARCHAR(20) NOT NULL DEFAULT 'EXACT', -- EXACT, PHRASE, FUZZY
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_intent_keywords_intent ON system.intent_keywords(intent_id);

-- Map which intents are expected/forbidden for each free-text step
CREATE TABLE system.step_intent_config (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    step_id       UUID NOT NULL REFERENCES system.simulation_steps(id) ON DELETE CASCADE,
    intent_id     UUID NOT NULL REFERENCES system.security_intents(id) ON DELETE CASCADE,
    is_mandatory  BOOLEAN NOT NULL DEFAULT FALSE, -- absence = auto fail
    weight        INTEGER NOT NULL DEFAULT 1,
    UNIQUE(step_id, intent_id)
);
CREATE INDEX idx_step_intent_step ON system.step_intent_config(step_id);

-- Per-scenario score configuration
CREATE TABLE system.scenario_score_config (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scenario_id         UUID NOT NULL REFERENCES system.simulation_scenarios(id) ON DELETE CASCADE UNIQUE,
    pass_threshold      INTEGER NOT NULL DEFAULT 70,
    critical_threshold  INTEGER NOT NULL DEFAULT 50,
    max_score           INTEGER NOT NULL DEFAULT 100,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
