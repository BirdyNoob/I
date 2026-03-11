CREATE TABLE system.lessons (
    id UUID PRIMARY KEY,
    module_id UUID REFERENCES system.modules(id),
    title TEXT,
    lesson_type TEXT,
    content_json JSONB,
    video_url TEXT,
    resource_url TEXT,
    sort_order INT,
    is_published BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);