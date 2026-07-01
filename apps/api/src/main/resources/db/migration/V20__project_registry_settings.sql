-- Per-project heartbeat liveness thresholds (override the global defaults).
-- interval_seconds = ONLINE band (expected heartbeat interval);
-- stale_after_seconds = STALE band end; timeout_seconds = hard OFFLINE/expiry cap.
CREATE TABLE project_registry_settings (
    project_id UUID PRIMARY KEY REFERENCES projects (id),
    interval_seconds INTEGER NOT NULL,
    stale_after_seconds INTEGER NOT NULL,
    timeout_seconds INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
