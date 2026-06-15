-- ============================================================
-- Seed: DATA_LEAKAGE scenario "Vendor Briefing Prep"
-- 3 steps: INFORMATION → MCQ → FREE_TEXT
-- Intents, keywords, score config all included
-- ============================================================

-- ── Simulation (attach to first available simulation or use a fixed ID) ──────
-- We insert a standalone scenario linked to a dummy simulation_id placeholder.
-- In production, replace the simulation_id with a real track's simulation UUID.

DO $$
DECLARE
    v_sim_id       UUID := 'a0000000-0000-0000-0000-000000000001';
    v_scenario_id  UUID := 'a0000000-0000-0000-0000-000000000010';
    v_step1_id     UUID := 'a0000000-0000-0000-0000-000000000020';
    v_step2_id     UUID := 'a0000000-0000-0000-0000-000000000021';
    v_step3_id     UUID := 'a0000000-0000-0000-0000-000000000022';

    -- Intents
    v_intent_redact      UUID := 'b0000000-0000-0000-0000-000000000001';
    v_intent_classify    UUID := 'b0000000-0000-0000-0000-000000000002';
    v_intent_minimize    UUID := 'b0000000-0000-0000-0000-000000000003';
    v_intent_upload_ai   UUID := 'b0000000-0000-0000-0000-000000000004';
    v_intent_share_conf  UUID := 'b0000000-0000-0000-0000-000000000005';
BEGIN

-- ── Simulation stub (only if no real simulation exists) ────────────────
-- First ensure a placeholder track exists for the simulation FK
INSERT INTO system.tracks (id, slug, title, description, status, created_at)
VALUES ('a0000000-0000-0000-0000-000000000099', 'ai-security-seed', 'AI Security Track (Seed)', 'Placeholder track for simulation seeding', 'DRAFT', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO system.simulations (id, track_id, title, description, simulation_type, difficulty_level, scenario_prompt, estimated_mins, published)
VALUES (v_sim_id,
       'a0000000-0000-0000-0000-000000000099',
       'AI Security Simulations',
       'NLP-Lite simulation engine scenarios',
       'DATA_LEAKAGE', 'INTERMEDIATE', 'Multi-step security simulation', 15, TRUE)
ON CONFLICT (id) DO NOTHING;

-- ── Scenario ───────────────────────────────────────────────────────────
INSERT INTO system.simulation_scenarios
    (id, simulation_id, title, description, domain, difficulty, sort_order, published, created_at, updated_at)
VALUES
    (v_scenario_id, v_sim_id,
     'Vendor Briefing Prep',
     'You need to prepare talking points for an external vendor using an AI assistant. Your source document contains sensitive company data mixed with general information.',
     'DATA_LEAKAGE', 'INTERMEDIATE', 0, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;

-- ── Step 1: INFORMATION (context setting) ──────────────────────────────
INSERT INTO system.simulation_steps
    (id, scenario_id, step_type, title, content, context_json, sort_order, is_mandatory, created_at)
VALUES (
    v_step1_id, v_scenario_id, 'INFORMATION',
    'Scenario: Preparing for a Vendor Meeting',
    'You are a business analyst at Acme Corp. You have been asked to prepare talking points for an upcoming vendor meeting. You have access to an internal strategy document. Your manager has asked you to use the company AI assistant to draft the talking points. Read the document carefully before proceeding.',
    '{
        "artifact_type": "document",
        "label": "Q3 Strategy Document (CONFIDENTIAL)",
        "content": "Q3 Revenue Target: $4.2M (do not disclose)\nVendor Name: TechPro Solutions\nProject Codename: TITAN\nAWS API Key: AKIA3X7MNOP8QRST\nGeneral talking points: improve delivery timelines, discuss SLA, review pricing tiers\nEmployee contact: sarah.chen@acme.com (procurement lead)\nCompetitor intel: RivalCorp is planning a Q4 launch"
    }',
    0, FALSE, NOW()
) ON CONFLICT DO NOTHING;

-- ── Step 2: MCQ — identify what should NOT be shared ─────────────────
INSERT INTO system.simulation_steps
    (id, scenario_id, step_type, title, content, sort_order, is_mandatory, created_at)
VALUES (
    v_step2_id, v_scenario_id, 'MCQ',
    'Identify Sensitive Information',
    'Which of the following pieces of information from the document should NEVER be included in a prompt sent to an external AI assistant? Select ALL that apply.',
    1, TRUE, NOW()
) ON CONFLICT DO NOTHING;

-- MCQ options
INSERT INTO system.simulation_questions (id, step_id, question_type, option_text, is_correct, points, feedback_text, sort_order)
VALUES
    (gen_random_uuid(), v_step2_id, 'MULTI_SELECT', 'Q3 Revenue Target: $4.2M',              TRUE,  10, 'Correct — financial targets are confidential and must not leave internal systems.', 0),
    (gen_random_uuid(), v_step2_id, 'MULTI_SELECT', 'Project Codename: TITAN',                TRUE,  10, 'Correct — internal project codenames reveal strategic plans to competitors.', 1),
    (gen_random_uuid(), v_step2_id, 'MULTI_SELECT', 'AWS API Key: AKIA3X7MNOP8QRST',          TRUE,  15, 'Correct — API keys are credentials. Exposing them can lead to a full account compromise.', 2),
    (gen_random_uuid(), v_step2_id, 'MULTI_SELECT', 'Employee contact: sarah.chen@acme.com',  TRUE,  10, 'Correct — employee PII must not be shared with external AI tools.', 3),
    (gen_random_uuid(), v_step2_id, 'MULTI_SELECT', 'Competitor intel: RivalCorp Q4 launch',  TRUE,  10, 'Correct — competitive intelligence is highly sensitive strategic information.', 4),
    (gen_random_uuid(), v_step2_id, 'MULTI_SELECT', 'Vendor Name: TechPro Solutions',         FALSE, 10, 'Incorrect — the vendor name is public information and safe to include.', 5),
    (gen_random_uuid(), v_step2_id, 'MULTI_SELECT', 'General SLA and pricing discussion',     FALSE, 10, 'Incorrect — general meeting agenda items are safe to use in AI prompts.', 6)
ON CONFLICT DO NOTHING;

-- ── Step 3: FREE_TEXT — write a safe AI prompt ────────────────────────
INSERT INTO system.simulation_steps
    (id, scenario_id, step_type, title, content, sort_order, is_mandatory, created_at)
VALUES (
    v_step3_id, v_scenario_id, 'FREE_TEXT',
    'Write a Safe AI Prompt',
    'Using ONLY the non-sensitive information from the document, write a prompt you would send to the AI assistant to generate vendor meeting talking points. Do NOT include any confidential data.',
    2, TRUE, NOW()
) ON CONFLICT DO NOTHING;

-- ── Security Intents ──────────────────────────────────────────────────
INSERT INTO system.security_intents (id, intent_code, description, intent_type, base_points, created_at)
VALUES
    (v_intent_redact,     'REDACT_DATA',          'Learner removes or avoids sensitive data',              'POSITIVE', 15, NOW()),
    (v_intent_classify,   'CLASSIFY_DATA',        'Learner identifies the sensitivity of information',     'POSITIVE', 10, NOW()),
    (v_intent_minimize,   'MINIMIZE_DATA_EXPOSURE','Learner uses only necessary data (data minimization)', 'POSITIVE', 15, NOW()),
    (v_intent_upload_ai,  'UPLOAD_TO_AI',         'Learner pastes confidential data into AI prompt',       'NEGATIVE', 30, NOW()),
    (v_intent_share_conf, 'SHARE_CONFIDENTIAL_DATA','Learner includes internal figures or credentials',    'NEGATIVE', 40, NOW())
ON CONFLICT (intent_code) DO NOTHING;

-- ── Intent Keywords ───────────────────────────────────────────────────
-- REDACT_DATA
INSERT INTO system.intent_keywords (id, intent_id, keyword, match_type, created_at) VALUES
    (gen_random_uuid(), v_intent_redact, 'redact', 'FUZZY', NOW()),
    (gen_random_uuid(), v_intent_redact, 'remove sensitive', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_redact, 'exclude confidential', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_redact, 'without the internal', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_redact, 'only public information', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_redact, 'not include any sensitive', 'PHRASE', NOW())
ON CONFLICT DO NOTHING;

-- CLASSIFY_DATA
INSERT INTO system.intent_keywords (id, intent_id, keyword, match_type, created_at) VALUES
    (gen_random_uuid(), v_intent_classify, 'confidential', 'EXACT', NOW()),
    (gen_random_uuid(), v_intent_classify, 'sensitive information', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_classify, 'internal data', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_classify, 'classified', 'EXACT', NOW())
ON CONFLICT DO NOTHING;

-- MINIMIZE_DATA_EXPOSURE
INSERT INTO system.intent_keywords (id, intent_id, keyword, match_type, created_at) VALUES
    (gen_random_uuid(), v_intent_minimize, 'only the general', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_minimize, 'talking points', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_minimize, 'delivery timelines', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_minimize, 'pricing tiers', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_minimize, 'sla', 'EXACT', NOW()),
    (gen_random_uuid(), v_intent_minimize, 'service level', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_minimize, 'minimize', 'FUZZY', NOW())
ON CONFLICT DO NOTHING;

-- UPLOAD_TO_AI (negative — penalise)
INSERT INTO system.intent_keywords (id, intent_id, keyword, match_type, created_at) VALUES
    (gen_random_uuid(), v_intent_upload_ai, '4.2m', 'EXACT', NOW()),
    (gen_random_uuid(), v_intent_upload_ai, '4.2 million', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_upload_ai, 'akia', 'EXACT', NOW()),
    (gen_random_uuid(), v_intent_upload_ai, 'api key', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_upload_ai, 'titan', 'EXACT', NOW())
ON CONFLICT DO NOTHING;

-- SHARE_CONFIDENTIAL_DATA (negative — penalise)
INSERT INTO system.intent_keywords (id, intent_id, keyword, match_type, created_at) VALUES
    (gen_random_uuid(), v_intent_share_conf, 'sarah.chen', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_share_conf, 'rivalcorp', 'EXACT', NOW()),
    (gen_random_uuid(), v_intent_share_conf, 'competitor', 'EXACT', NOW()),
    (gen_random_uuid(), v_intent_share_conf, 'q4 launch', 'PHRASE', NOW()),
    (gen_random_uuid(), v_intent_share_conf, 'revenue target', 'PHRASE', NOW())
ON CONFLICT DO NOTHING;

-- ── Step-Intent Config for FREE_TEXT step ─────────────────────────────
INSERT INTO system.step_intent_config (id, step_id, intent_id, is_mandatory, weight)
VALUES
    (gen_random_uuid(), v_step3_id, v_intent_redact,     TRUE,  2),   -- mandatory: must show redaction intent
    (gen_random_uuid(), v_step3_id, v_intent_minimize,   FALSE, 2),   -- rewarded for data minimization
    (gen_random_uuid(), v_step3_id, v_intent_classify,   FALSE, 1),   -- rewarded for classification awareness
    (gen_random_uuid(), v_step3_id, v_intent_upload_ai,  FALSE, 1),   -- penalised if triggered
    (gen_random_uuid(), v_step3_id, v_intent_share_conf, FALSE, 2)    -- penalised if triggered
ON CONFLICT DO NOTHING;

-- ── Score Config ──────────────────────────────────────────────────────
INSERT INTO system.scenario_score_config (id, scenario_id, pass_threshold, critical_threshold, max_score, created_at)
VALUES (gen_random_uuid(), v_scenario_id, 70, 50, 100, NOW())
ON CONFLICT DO NOTHING;

END $$;
