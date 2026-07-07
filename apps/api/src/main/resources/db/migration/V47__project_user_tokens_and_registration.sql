-- Track A foundation: verificación de email y reseteo self-service de contraseña
-- para project_users, más el toggle de registro público por proyecto.
--
-- Los tokens (verificación / reset) se guardan como hash SHA-256 hex (VARCHAR(64)),
-- nunca en claro. Son single-use: el consumo los pone a NULL, de modo que un replay
-- no vuelve a matchear. Llevan expiración propia. El secret TOTP / columnas MFA llegan
-- en una migración posterior junto con su lógica (M5).
ALTER TABLE project_users
    ADD COLUMN email_verification_token_hash VARCHAR(64),
    ADD COLUMN email_verification_expires_at TIMESTAMPTZ,
    ADD COLUMN password_reset_token_hash VARCHAR(64),
    ADD COLUMN password_reset_expires_at TIMESTAMPTZ;

-- Índices parciales para la búsqueda por token (sólo filas con token pendiente).
CREATE INDEX ix_project_users_email_verification_token
    ON project_users (email_verification_token_hash)
    WHERE email_verification_token_hash IS NOT NULL;
CREATE INDEX ix_project_users_password_reset_token
    ON project_users (password_reset_token_hash)
    WHERE password_reset_token_hash IS NOT NULL;

-- Toggle de registro público (self-signup) por proyecto. Ausente/false = sólo invitación.
ALTER TABLE projects
    ADD COLUMN public_registration_enabled BOOLEAN NOT NULL DEFAULT false;
