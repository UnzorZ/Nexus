-- Per-project external OIDC identity-provider configuration (Google federation).
-- One row per project. The client secret is stored encrypted (AES-256-GCM) in
-- client_secret_enc; the plaintext is never persisted.
CREATE TABLE project_oidc_idp (
    project_id UUID PRIMARY KEY REFERENCES projects (id),
    issuer VARCHAR(255) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    client_secret_enc TEXT NOT NULL,
    scope VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    auto_provision BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Federation links between a ProjectUser and an external IdP subject.
-- Within a project, one (provider, subject) maps to exactly one ProjectUser, and a
-- ProjectUser has at most one link per provider. This is the only structure that lets
-- a Google identity log in without re-linking on every visit.
CREATE TABLE project_user_oidc_links (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    project_user_id UUID NOT NULL REFERENCES project_users (id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email VARCHAR(320),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_oidc_link_project_provider_subject UNIQUE (project_id, provider, subject),
    CONSTRAINT uk_oidc_link_user_provider UNIQUE (project_user_id, provider)
);

CREATE INDEX idx_project_user_oidc_links_project_provider
    ON project_user_oidc_links (project_id, provider);
