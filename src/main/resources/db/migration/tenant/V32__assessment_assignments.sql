CREATE TABLE assessment_assignments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    assessment_config_id VARCHAR(255) NOT NULL REFERENCES assessment_config(id),
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    due_date TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL
);

CREATE INDEX idx_assessment_assignments_user_id ON assessment_assignments(user_id);
CREATE INDEX idx_assessment_assignments_config_id ON assessment_assignments(assessment_config_id);
