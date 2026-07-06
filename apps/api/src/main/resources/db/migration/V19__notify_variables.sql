-- Declared variables (name -> default value) per notification template.
ALTER TABLE notification_templates ADD COLUMN variables TEXT;

-- Project-level global variables applied to every notification (overridable per send).
CREATE TABLE project_notify_variables (
    project_id UUID PRIMARY KEY REFERENCES projects (id),
    variables TEXT,
    updated_at TIMESTAMPTZ NOT NULL
);
