CREATE TABLE user_assignments (

    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    track_id UUID NOT NULL,

    assigned_at TIMESTAMPTZ DEFAULT NOW(),

    due_date TIMESTAMPTZ,

    status TEXT,

    content_version_at_assignment SMALLINT

);