-- Audit log (módulo audit, ADR-0004). Append-only record of state changes and
-- auth rejections across modules (api keys, members, roles, permissions, modules,
-- projects). project_id is nullable (anonymous auth rejections have none).
-- resource_id is a string (UUID or module key). metadata_json is free-form TEXT
-- (Jackson-3 converter-friendly, same as heartbeats). No FK on project_id so the
-- log survives project/key hard-deletes and stores anonymous events.

CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    project_id UUID,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64),
    resource_id VARCHAR(64),
    outcome VARCHAR(16) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id VARCHAR(64),
    ip VARCHAR(64),
    user_agent VARCHAR(255),
    trace_id VARCHAR(64),
    metadata_json TEXT,
    occurred_at TIMESTAMPTZ NOT NULL
);

-- Panel list: most recent events for a project.
CREATE INDEX ix_audit_log_project_time
    ON audit_log (project_id, occurred_at DESC);

-- Future retention purge (>90d).
CREATE INDEX ix_audit_log_occurred_at
    ON audit_log (occurred_at);
