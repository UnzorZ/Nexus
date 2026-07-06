-- Soporte de alertas offline (heartbeat → notify, spec §13.1 "offline detection can
-- later trigger Notify"). Dos piezas:
--  1) project_heartbeats.offline_notified_at: marca "ya se avisó de ESTA caída".
--     Se setea al disparar la alerta y se limpia en el siguiente latido (touch →
--     la instancia se recuperó y se rearma para la próxima caída). Dedup por
--     outage, de ownership del módulo registry.
--  2) project_registry_settings.offline_notify_{enabled,email}: config por proyecto
--     (toggle + destinatario). Ausente = desactivado.

ALTER TABLE project_heartbeats ADD COLUMN offline_notified_at TIMESTAMPTZ;

-- Índice parcial para el barrido del detector: sólo las filas pendientes de avisar.
CREATE INDEX ix_project_heartbeats_offline_pending
    ON project_heartbeats (last_seen_at) WHERE offline_notified_at IS NULL;

ALTER TABLE project_registry_settings
    ADD COLUMN offline_notify_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN offline_notify_email VARCHAR(320);
