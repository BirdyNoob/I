-- V42: Add completed_at to user_assignments for accurate completion trend KPI
-- Without this, computeCompletionDelta() was filtering by assignedAt (creation date)
-- which measures assignment activity, not actual learner completions.

ALTER TABLE user_assignments
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

-- Back-fill: for already-COMPLETED rows set completed_at = assignedAt as a safe fallback.
-- (Real value unknown from history; this keeps old rows from producing NULL gaps.)
UPDATE user_assignments
SET completed_at = assigned_at
WHERE status = 'COMPLETED'
  AND completed_at IS NULL;

-- Index for fast range queries used by the delta KPI
CREATE INDEX IF NOT EXISTS idx_user_assignments_completed_at
    ON user_assignments (completed_at)
    WHERE completed_at IS NOT NULL;
