-- Multi-recipient offline alerts: replace the single offline_notify_email column
-- with a newline-separated list of recipients (offline_notify_recipients TEXT).
-- Existing single-email values are migrated into a one-element list; the old
-- column is dropped. The list is read/written via registry.domain.StringListConverter.
ALTER TABLE project_registry_settings ADD COLUMN offline_notify_recipients TEXT NOT NULL DEFAULT '';

UPDATE project_registry_settings
   SET offline_notify_recipients = offline_notify_email
 WHERE offline_notify_email IS NOT NULL AND offline_notify_email <> '';

ALTER TABLE project_registry_settings DROP COLUMN offline_notify_email;
