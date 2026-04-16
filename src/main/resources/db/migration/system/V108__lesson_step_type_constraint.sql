-- V108__lesson_step_type_constraint.sql
-- ─────────────────────────────────────────────────────────────────────────────
-- Adds a CHECK constraint that enforces the canonical StepType enum values on
-- the lesson_steps.step_type column.
--
-- Canonical types (from Module_1_json.json canonical format):
--   CONCEPT   — key-points + principle card
--   SCENARIO  — interactive multi-choice scenario
--   QUIZ      — multi-question knowledge check
--   DO_DONT   — dos and don'ts action list
--   SUMMARY   — key takeaway card
--
-- Legacy / alias types (kept for backwards compatibility):
--   HOOK      — intro hook card (pre-migration alias for CONCEPT)
--   VIDEO     — video-only step (pre-migration alias for CONCEPT with videoUrl)
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE system.lesson_steps
    ADD CONSTRAINT chk_lesson_step_type
        CHECK (step_type IN (
            'CONCEPT',
            'SCENARIO',
            'QUIZ',
            'DO_DONT',
            'SUMMARY',
            'HOOK',
            'VIDEO'
        ));
