CREATE TABLE IF NOT EXISTS quiz_attempts (

    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    lesson_id UUID NOT NULL,
    score INT NOT NULL,
    total_questions INT NOT NULL,
    attempted_at TIMESTAMPTZ DEFAULT NOW()

);