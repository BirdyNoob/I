-- V125: Normalize assessment storage from JSONB blob into relational tables.
-- Replaces the monolithic assessment_config.config_data JSON with proper tables.

CREATE TABLE system.assessments (
    id              VARCHAR(255) PRIMARY KEY,
    track_id        VARCHAR(255) NOT NULL,
    title           TEXT NOT NULL,
    subtitle        TEXT,
    track_name      TEXT,
    total_questions  INT NOT NULL DEFAULT 50,
    time_limit_seconds INT NOT NULL DEFAULT 3600,
    passing_score    INT NOT NULL DEFAULT 80,
    retake_policy    VARCHAR(50) NOT NULL DEFAULT 'UNLIMITED',
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE system.assessment_sections (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id   VARCHAR(255) NOT NULL REFERENCES system.assessments(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0
);

CREATE TABLE system.assessment_questions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id   VARCHAR(255) NOT NULL REFERENCES system.assessments(id) ON DELETE CASCADE,
    section_id      UUID REFERENCES system.assessment_sections(id) ON DELETE SET NULL,
    question_id     VARCHAR(100) NOT NULL,
    type            VARCHAR(50) NOT NULL DEFAULT 'MULTIPLE_CHOICE',
    topic           TEXT,
    difficulty      INT,
    scenario_context TEXT,
    text            TEXT NOT NULL,
    correct_option_id VARCHAR(100) NOT NULL,
    explanation     TEXT,
    sort_order      INT NOT NULL DEFAULT 0,
    UNIQUE (assessment_id, question_id)
);

CREATE TABLE system.assessment_options (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id     UUID NOT NULL REFERENCES system.assessment_questions(id) ON DELETE CASCADE,
    option_id       VARCHAR(100) NOT NULL,
    text            TEXT NOT NULL,
    explanation     TEXT,
    sort_order      INT NOT NULL DEFAULT 0
);

CREATE TABLE system.assessment_images (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id     UUID NOT NULL REFERENCES system.assessment_questions(id) ON DELETE CASCADE,
    file_name       TEXT,
    mime_type       VARCHAR(100),
    alt_text        TEXT,
    data            BYTEA,
    image_url       TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT one_image_per_question UNIQUE (question_id)
);

-- Indexes
CREATE INDEX idx_assessments_track_id ON system.assessments(track_id);
CREATE INDEX idx_assessment_sections_assessment ON system.assessment_sections(assessment_id);
CREATE INDEX idx_assessment_questions_assessment ON system.assessment_questions(assessment_id);
CREATE INDEX idx_assessment_questions_section ON system.assessment_questions(section_id);
CREATE INDEX idx_assessment_options_question ON system.assessment_options(question_id);
