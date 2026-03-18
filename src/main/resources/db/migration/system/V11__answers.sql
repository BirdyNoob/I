CREATE TABLE system.answers (

    id UUID PRIMARY KEY,

    question_id UUID NOT NULL REFERENCES system.questions(id),

    answer_text TEXT NOT NULL,

    is_correct BOOLEAN DEFAULT FALSE

);