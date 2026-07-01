CREATE TABLE project_config (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    key VARCHAR(128) NOT NULL,
    value TEXT NOT NULL,
    value_type VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_project_config_project_key UNIQUE (project_id, key)
);

CREATE INDEX idx_project_config_project ON project_config (project_id);
