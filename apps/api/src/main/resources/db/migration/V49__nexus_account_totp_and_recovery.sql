-- MFA TOTP para cuentas del panel (NexusAccount). Espejo de V48 (ProjectUser).
--
-- El secret TOTP debe ser REVERSIBLE (hace falta el plaintext para computar los
-- códigos de 30s), así que se guarda cifrado AES-256-GCM (totp_secret_enc), nunca en
-- claro ni hasheado. totp_enabled_at marca la activación (null = no inscrita); el flag
-- existente mfa_enabled se mantiene sincronizado como fuente legible por los DTOs.
--
-- Los recovery codes son verificadores single-use: hash SHA-256 hex (VARCHAR(64)) y se
-- marcan consumidos al usarse. Nunca reversibles.
ALTER TABLE nexus_accounts
    ADD COLUMN totp_secret_enc TEXT,
    ADD COLUMN totp_enabled_at TIMESTAMPTZ;

CREATE TABLE nexus_account_recovery_codes (
    id UUID PRIMARY KEY,
    nexus_account_id UUID NOT NULL REFERENCES nexus_accounts (id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX ix_nexus_account_recovery_codes_hash
    ON nexus_account_recovery_codes (code_hash)
    WHERE consumed_at IS NULL;
CREATE INDEX ix_nexus_account_recovery_codes_account
    ON nexus_account_recovery_codes (nexus_account_id)
    WHERE consumed_at IS NULL;
