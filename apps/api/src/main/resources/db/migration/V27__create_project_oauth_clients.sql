-- Per-project OAuth/OIDC clients (spec §9.6, §15.3). Each project issues tokens
-- under its own issuer {origin}/p/{slug} (B2 multi-issuer). client_id is globally
-- unique; redirect URIs match exactly; PKCE is required for public (no-secret)
-- clients. The secret is {bcrypt}-hashed (verified by the shared PasswordEncoder);
-- NULL for public clients.

CREATE TABLE project_oauth_clients (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    client_id VARCHAR(100) NOT NULL,
    client_secret_hash VARCHAR(200),
    name VARCHAR(200) NOT NULL,
    redirect_uris TEXT NOT NULL,
    post_logout_redirect_uris TEXT,
    grant_types TEXT NOT NULL,
    scopes TEXT NOT NULL,
    require_pkce BOOLEAN NOT NULL DEFAULT TRUE,
    consent_required BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL,
    created_by_account_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_project_oauth_clients_client_id UNIQUE (client_id)
);

CREATE INDEX ix_project_oauth_clients_project
    ON project_oauth_clients (project_id);
