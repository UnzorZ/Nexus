CREATE TABLE document_templates (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    name VARCHAR(120) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    template_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_document_templates_project_name UNIQUE (project_id, name)
);

CREATE INDEX idx_document_templates_project ON document_templates (project_id);

CREATE TABLE document_renders (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    template_id UUID,
    template_name VARCHAR(120) NOT NULL,
    variables TEXT,
    output TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_document_renders_project_time
    ON document_renders (project_id, created_at DESC);
