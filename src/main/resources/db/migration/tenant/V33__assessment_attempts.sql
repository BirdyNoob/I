CREATE TABLE assessment_attempts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    assessment_config_id VARCHAR(255) NOT NULL REFERENCES assessment_config(id),
    status VARCHAR(50) NOT NULL,
    score INTEGER,
    attempt_number INTEGER,
    date_completed TIMESTAMP WITH TIME ZONE,
    questions_answered INTEGER,
    total_questions INTEGER,
    certificate_id VARCHAR(255)
);

CREATE INDEX idx_assessment_attempts_user_id ON assessment_attempts(user_id);
CREATE INDEX idx_assessment_attempts_config_id ON assessment_attempts(assessment_config_id);
