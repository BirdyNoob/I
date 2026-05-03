CREATE TABLE assessment_config (
    id VARCHAR(255) PRIMARY KEY,
    track_id VARCHAR(255) NOT NULL,
    config_data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_assessment_config_track_id ON assessment_config (track_id);
