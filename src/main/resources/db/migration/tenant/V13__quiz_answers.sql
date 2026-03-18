CREATE TABLE IF NOT EXISTS quiz_answers (

    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL,
    question_id UUID NOT NULL,
    answer_id UUID NOT NULL,
    is_correct BOOLEAN

);