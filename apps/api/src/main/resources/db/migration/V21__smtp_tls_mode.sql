-- Modo de confianza TLS por proyecto (ADR-0013).
--   PUBLIC  -> truststore público por defecto (WebPKI): Gmail, SendGrid, LE, ...
--   PINNED  -> confiar sólo en la CA subida por el proyecto (self-signed/interna).
-- trusted_ca_pem sólo aplica en PINNED; en PUBLIC se ignora.
ALTER TABLE project_smtp_settings
    ADD COLUMN tls_mode VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN trusted_ca_pem TEXT;
