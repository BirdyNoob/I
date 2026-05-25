CREATE TABLE module_progress (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    module_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    spent_seconds INTEGER DEFAULT 0,
    last_active_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_module_progress_user_module UNIQUE (user_id, module_id)
);

CREATE INDEX idx_module_progress_user ON module_progress(user_id);
