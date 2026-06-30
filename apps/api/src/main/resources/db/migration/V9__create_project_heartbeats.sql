-- Project heartbeats (spec §9.11, §13.1). Each beat is reported by an app
-- instance authenticating with a project API key (spec §10.2). The instance key
-- is (project_id, instance_id) — multiple instances can report for the same
-- project. status is the client's self-reported value (default 'up'); the derived
-- ONLINE/STALE/OFFLINE liveness is computed at read time from last_seen_at and
-- the configured timeout, never stored. metadata_json is free-form (jsonb).

CREATE TABLE project_heartbeats (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    api_key_id UUID NOT NULL,
    api_key_prefix VARCHAR(64) NOT NULL,
    instance_id VARCHAR(128) NOT NULL,
    app_name VARCHAR(255) NOT NULL,
    app_version VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'up',
    metadata_json TEXT,
    last_seen_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_project_heartbeats_project_instance
        UNIQUE (project_id, instance_id)
);

CREATE INDEX ix_project_heartbeats_project
    ON project_heartbeats (project_id);
