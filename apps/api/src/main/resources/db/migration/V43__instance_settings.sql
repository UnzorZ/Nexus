-- Configuración de instancia gestionable por el operador desde el panel
-- (singleton, fila id=1). Valores writeable que antes eran sólo env o hardcode:
--   registration_open        -> política de alta de cuentas (true = abierta).
--                               Checked on POST /accounts (ADR-0010 bootstrap
--                               siempre permitido si no hay admin aún).
--   default_modules          -> claves (csv) de módulos activados por defecto en
--                               proyectos nuevos; NULL = catálogo del enum.
--   heartbeat_*_seconds      -> defaults de heartbeat de instancia (override del
--                               env); el proyecto puede sobreescribir.
-- Singleton reforzado por CHECK (id = 1).
CREATE TABLE instance_settings (
    id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    registration_open BOOLEAN NOT NULL DEFAULT TRUE,
    default_modules VARCHAR(500),
    heartbeat_interval_seconds INTEGER,
    heartbeat_timeout_seconds INTEGER,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
