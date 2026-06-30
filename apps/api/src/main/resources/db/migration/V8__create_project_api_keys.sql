-- Project API keys (spec §9.3). Identify a project to Nexus for the project API
-- (X-Nexus-Api-Key, spec §10.2). Only key_prefix (lookup) + key_hash (verify,
-- SHA-256) are stored; the full secret is shown once at creation/rotation.

CREATE TABLE project_api_keys (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    name VARCHAR(120) NOT NULL,
    key_prefix VARCHAR(32) NOT NULL,
    key_hash VARCHAR(128) NOT NULL,
    scopes TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_by_account_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_project_api_keys_project_prefix
        UNIQUE (project_id, key_prefix)
);

CREATE INDEX ix_project_api_keys_prefix
    ON project_api_keys (key_prefix);
