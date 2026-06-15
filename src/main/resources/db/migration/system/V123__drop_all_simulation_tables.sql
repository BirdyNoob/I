-- Drop all simulation-related tables (removing simulation module entirely)
DROP TABLE IF EXISTS system.step_intent_config CASCADE;
DROP TABLE IF EXISTS system.intent_keywords CASCADE;
DROP TABLE IF EXISTS system.security_intents CASCADE;
DROP TABLE IF EXISTS system.simulation_questions CASCADE;
DROP TABLE IF EXISTS system.simulation_steps CASCADE;
DROP TABLE IF EXISTS system.scenario_score_config CASCADE;
DROP TABLE IF EXISTS system.simulation_scenarios CASCADE;
DROP TABLE IF EXISTS system.simulation_score_configs CASCADE;
DROP TABLE IF EXISTS system.simulation_evaluation_rules CASCADE;
DROP TABLE IF EXISTS system.simulation_choices CASCADE;
DROP TABLE IF EXISTS system.simulations CASCADE;
