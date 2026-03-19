CREATE TABLE system.certificates (

    id UUID PRIMARY KEY,
    track_id UUID NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()

);