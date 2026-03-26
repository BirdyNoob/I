UPDATE user_assignments
SET status = 'ASSIGNED'
WHERE status IS NULL
   OR status NOT IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'OVERDUE', 'FAILED');

ALTER TABLE user_assignments
    DROP CONSTRAINT IF EXISTS chk_user_assignments_status;

ALTER TABLE user_assignments
    ADD CONSTRAINT chk_user_assignments_status
    CHECK (status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'OVERDUE', 'FAILED'));
