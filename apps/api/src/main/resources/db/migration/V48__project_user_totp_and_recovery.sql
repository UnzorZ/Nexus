-- M5: TOTP MFA para usuarios finales (ProjectUser).
--
-- El secret TOTP compartido debe ser REVERSIBLE (hace falta el plaintext para
-- computar los códigos de 30s), así que se guarda cifrado AES-256-GCM
-- (totp_secret_enc), nunca en claro ni hasheado. totp_enabled_at marca la
-- activación (null = no inscrita).
--
-- Los recovery codes son verificadores single-use: se guardan como hash SHA-256
-- hex (VARCHAR(64), como los tokens de verify/reset de V47) y se marcan consumidos
-- al usarse. Nunca reversibles.
ALTER TABLE project_users
    ADD COLUMN totp_secret_enc TEXT,
    ADD COLUMN totp_enabled_at TIMESTAMPTZ;

CREATE TABLE project_user_recovery_codes (
    id UUID PRIMARY KEY,
    project_user_id UUID NOT NULL REFERENCES project_users (id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

-- Búsqueda por hash de código (login por recovery) y listado por usuario.
CREATE INDEX ix_project_user_recovery_codes_hash
    ON project_user_recovery_codes (code_hash)
    WHERE consumed_at IS NULL;
CREATE INDEX ix_project_user_recovery_codes_user
    ON project_user_recovery_codes (project_user_id)
    WHERE consumed_at IS NULL;
