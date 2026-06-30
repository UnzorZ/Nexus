-- Permission catalog (§9.7), project roles and role→permission grants (§9.8).
-- Source/sync columns (source, deprecated, missing_from_last_sync,
-- last_declared_at) are reserved for future app-sync; the MVP only writes
-- source='WEB'. role→permission grants reference free-form permission keys
-- (no FK) to support wildcard entries like 'orders.*' or '*'.

CREATE TABLE project_permissions (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    key VARCHAR(128) NOT NULL,
    label VARCHAR(120) NOT NULL,
    description TEXT,
    source VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL,
    deprecated BOOLEAN NOT NULL,
    missing_from_last_sync BOOLEAN NOT NULL,
    last_declared_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_project_permissions_project_key
        UNIQUE (project_id, key)
);

CREATE TABLE project_roles (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    key VARCHAR(128) NOT NULL,
    label VARCHAR(120) NOT NULL,
    description TEXT,
    system BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_project_roles_project_key
        UNIQUE (project_id, key)
);

CREATE TABLE project_role_permissions (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    role_id UUID NOT NULL REFERENCES project_roles (id) ON DELETE CASCADE,
    permission_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_project_role_permissions_role_key
        UNIQUE (role_id, permission_key)
);

CREATE INDEX ix_project_role_permissions_project_role
    ON project_role_permissions (project_id, role_id);
