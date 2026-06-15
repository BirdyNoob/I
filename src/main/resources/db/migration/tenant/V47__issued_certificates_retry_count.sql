-- V47: Add retry_count column to issued_certificates for the automated retry scheduler.
-- Caps automated retries at 3 attempts to prevent infinite loops on permanently broken records.
ALTER TABLE issued_certificates
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;
