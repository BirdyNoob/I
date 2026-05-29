CREATE TABLE learner_stats (
    user_id UUID PRIMARY KEY,
    total_xp INT NOT NULL DEFAULT 0,
    current_streak INT NOT NULL DEFAULT 0,
    longest_streak INT NOT NULL DEFAULT 0,
    last_active_date DATE,
    leaderboard_opt_in BOOLEAN NOT NULL DEFAULT TRUE,
    anonymous_mode BOOLEAN NOT NULL DEFAULT FALSE,
    anonymous_alias VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE xp_transaction_log (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    xp_granted INT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    reference_entity_type VARCHAR(50),
    reference_entity_id UUID,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_xp_log_user_event ON xp_transaction_log(user_id, event_type, reference_entity_id);
