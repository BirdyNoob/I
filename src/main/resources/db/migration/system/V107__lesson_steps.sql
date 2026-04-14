ALTER TABLE system.lessons DROP COLUMN lesson_type;
ALTER TABLE system.lessons DROP COLUMN content_json;
ALTER TABLE system.lessons DROP COLUMN video_url;
ALTER TABLE system.lessons DROP COLUMN resource_url;
ALTER TABLE system.lessons ADD COLUMN estimated_mins INT DEFAULT 0;

CREATE TABLE system.lesson_steps (
    id UUID PRIMARY KEY,
    lesson_id UUID REFERENCES system.lessons(id),
    step_type TEXT NOT NULL,
    title TEXT,
    content_json JSONB,
    sort_order INT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_step_order UNIQUE (lesson_id, sort_order)
);
