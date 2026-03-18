CREATE TABLE system.questions (

    id UUID PRIMARY KEY,

    lesson_id UUID NOT NULL,

    question_text TEXT NOT NULL,

    question_type TEXT NOT NULL,

    created_at TIMESTAMPTZ DEFAULT NOW()

);