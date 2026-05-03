CREATE TABLE system.cheat_sheets (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    type_code VARCHAR(50) NOT NULL,
    department VARCHAR(255),
    description TEXT,
    data JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_cheat_sheets_department ON system.cheat_sheets(department);
