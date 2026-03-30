WITH ranked_assignments AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, track_id
               ORDER BY assigned_at DESC NULLS LAST, id DESC
           ) AS row_num
    FROM user_assignments
)
DELETE FROM user_assignments
WHERE id IN (
    SELECT id
    FROM ranked_assignments
    WHERE row_num > 1
);

ALTER TABLE user_assignments
    ADD CONSTRAINT uk_user_assignments_user_track UNIQUE (user_id, track_id);
