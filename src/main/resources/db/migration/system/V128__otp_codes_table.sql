-- OTP codes stored in DB for multi-instance support
CREATE TABLE system.otp_codes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       TEXT NOT NULL,
    otp         TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_otp_codes_email ON system.otp_codes(email);
CREATE INDEX idx_otp_codes_expires ON system.otp_codes(expires_at);
