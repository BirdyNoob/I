-- Drop all simulation-related tenant tables
DROP TABLE IF EXISTS sim_step_attempts CASCADE;
DROP TABLE IF EXISTS sim_scenario_attempts CASCADE;
DROP TABLE IF EXISTS simulation_rule_violations CASCADE;
DROP TABLE IF EXISTS simulation_choices_log CASCADE;
DROP TABLE IF EXISTS simulation_attempts CASCADE;

-- Remove simulation columns from lesson_progress
ALTER TABLE lesson_progress DROP COLUMN IF EXISTS simulation_scores;
ALTER TABLE lesson_progress DROP COLUMN IF EXISTS decision_history;
