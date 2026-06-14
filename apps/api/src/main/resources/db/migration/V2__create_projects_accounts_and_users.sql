CREATE TABLE projects (
    id UUID PRIMARY KEY,
    slug VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    public_base_url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE nexus_accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    instance_admin BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_nexus_accounts_email UNIQUE (email)
);

CREATE TABLE project_memberships (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    nexus_account_id UUID NOT NULL REFERENCES nexus_accounts (id),
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_project_membership_project_account
        UNIQUE (project_id, nexus_account_id)
);

CREATE TABLE project_users (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    email VARCHAR(320) NOT NULL,
    username VARCHAR(120),
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    authz_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_project_user_project_email UNIQUE (project_id, email)
);

CREATE INDEX idx_project_memberships_account
    ON project_memberships (nexus_account_id);

CREATE UNIQUE INDEX uk_nexus_accounts_one_instance_admin
    ON nexus_accounts (instance_admin)
    WHERE instance_admin;

CREATE INDEX idx_project_users_project
    ON project_users (project_id);
