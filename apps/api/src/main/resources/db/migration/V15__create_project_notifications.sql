CREATE TABLE notification_templates (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    name VARCHAR(120) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body_template TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_notification_templates_project_name UNIQUE (project_id, name)
);

CREATE INDEX idx_notification_templates_project ON notification_templates (project_id);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    channel VARCHAR(16) NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    template_id UUID,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    error VARCHAR(500),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notifications_project_time ON notifications (project_id, created_at DESC);
