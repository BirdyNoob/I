CREATE TABLE assessment_reset_logs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    assessment_config_id VARCHAR(255) NOT NULL,
    manager_id UUID NOT NULL,
    reset_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts_count INTEGER NOT NULL
);

CREATE INDEX idx_assessment_reset_logs_user_config ON assessment_reset_logs(user_id, assessment_config_id);
