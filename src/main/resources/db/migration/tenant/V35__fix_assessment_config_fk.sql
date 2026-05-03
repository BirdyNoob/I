-- V35: Migrate assessment_config out of tenant schema → system schema
--
-- assessment_config is now a global platform resource stored in system.assessment_config
-- (created by system migration V110). The tenant-level copy created by V31 is redundant.
--
-- Steps:
--   1. Drop FK constraints that referenced the (now-deleted) tenant-level table.
--   2. Drop the tenant-level assessment_config table itself.
--      Any data that existed in the tenant schema is discarded; the authoritative
--      table is in the system schema and managed by the platform admin.

-- Step 1: Drop cross-schema FK constraints
ALTER TABLE assessment_attempts
    DROP CONSTRAINT IF EXISTS assessment_attempts_assessment_config_id_fkey;

ALTER TABLE assessment_assignments
    DROP CONSTRAINT IF EXISTS assessment_assignments_assessment_config_id_fkey;

-- Step 2: Remove the redundant per-tenant table
DROP TABLE IF EXISTS assessment_config CASCADE;
