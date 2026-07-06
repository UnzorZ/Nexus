-- SMTP a nivel de instancia (singleton, fila id=1). Es la fuente por defecto
-- para el envío de email de TODOS los proyectos (ADR-0014); un proyecto puede
-- sobreescribirla con project_smtp_settings. Si la fila no existe, el envío cae
-- a nexus.notify.smtp.* (env). La contraseña se guarda cifrada (AES-GCM).
--   PUBLIC  -> truststore público por defecto (WebPKI): Gmail, SendGrid, LE, ...
--   PINNED  -> confiar sólo en la CA subida por el operador (self-signed/interna).
-- Singleton reforzado por CHECK (id = 1): nunca puede haber más de una fila.
CREATE TABLE instance_smtp_settings (
    id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    host VARCHAR(255),
    port INTEGER,
    username VARCHAR(255),
    from_address VARCHAR(255),
    password_enc TEXT,
    tls_mode VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    trusted_ca_pem TEXT,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
