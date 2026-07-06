-- Cipher used to encrypt each secret (AES_256_GCM by default; ChaCha20-Poly1305 optional).
ALTER TABLE project_secrets ADD COLUMN cipher VARCHAR(24) NOT NULL DEFAULT 'AES_256_GCM';

-- Per-project master-key override (wrapped with the global master key). NULL => global key.
CREATE TABLE project_vault_settings (
    project_id UUID PRIMARY KEY REFERENCES projects (id),
    master_key_enc TEXT,
    updated_at TIMESTAMPTZ NOT NULL
);
