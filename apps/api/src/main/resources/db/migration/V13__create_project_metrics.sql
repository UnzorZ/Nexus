CREATE TABLE project_metrics (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    name VARCHAR(128) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    tags TEXT,
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_project_metrics_project_name_time
    ON project_metrics (project_id, name, recorded_at DESC);
