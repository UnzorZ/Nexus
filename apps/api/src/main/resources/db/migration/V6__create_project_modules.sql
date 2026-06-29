CREATE TABLE project_modules (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    module VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_project_module_project_module
        UNIQUE (project_id, module)
);
