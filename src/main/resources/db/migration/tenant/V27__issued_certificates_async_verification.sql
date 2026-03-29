ALTER TABLE issued_certificates
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS file_name TEXT,
    ADD COLUMN IF NOT EXISTS blob_path TEXT,
    ADD COLUMN IF NOT EXISTS download_url TEXT,
    ADD COLUMN IF NOT EXISTS verification_token UUID,
    ADD COLUMN IF NOT EXISTS generated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS generation_error TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_issued_certificates_verification_token
    ON issued_certificates(verification_token);
