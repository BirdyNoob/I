-- Simulations stored as full JSONB documents (matches frontend schema exactly)
CREATE TABLE system.simulations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sim_id      TEXT UNIQUE NOT NULL,  -- slug e.g. "sim_prompt_injection"
    title       TEXT NOT NULL,
    data        JSONB NOT NULL,        -- full simulation JSON as consumed by frontend
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
