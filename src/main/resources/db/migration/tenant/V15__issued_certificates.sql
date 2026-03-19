CREATE TABLE IF NOT EXISTS issued_certificates (

    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    track_id UUID NOT NULL,
    certificate_id UUID NOT NULL,
    issued_at TIMESTAMPTZ DEFAULT NOW()

);