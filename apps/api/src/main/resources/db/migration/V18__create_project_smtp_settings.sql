CREATE TABLE project_smtp_settings (
    project_id UUID PRIMARY KEY REFERENCES projects (id),
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    username VARCHAR(255),
    from_address VARCHAR(255) NOT NULL,
    password_enc TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
