-- Endurecimiento del login de usuarios de proyecto: contador de intentos fallidos
-- y bloqueo temporal. Vive en Postgres (no en memoria) para que el bloqueo se
-- respete entre instancias y sobreviva a reinicios.
ALTER TABLE project_users
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMPTZ;
