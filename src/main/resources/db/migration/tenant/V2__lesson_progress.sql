CREATE TABLE lesson_progress (

    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    lesson_id UUID NOT NULL,

    status TEXT,

    completed_at TIMESTAMPTZ,

    requires_retraining BOOLEAN DEFAULT FALSE,

    created_at TIMESTAMPTZ DEFAULT NOW()

);