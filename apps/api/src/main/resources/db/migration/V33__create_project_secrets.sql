CREATE TABLE project_secrets (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    key VARCHAR(128) NOT NULL,
    ciphertext TEXT NOT NULL,
    nonce TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_rotated_at TIMESTAMPTZ,
    CONSTRAINT uk_project_secrets_project_key UNIQUE (project_id, key)
);

CREATE INDEX idx_project_secrets_project ON project_secrets (project_id);
