-- Per-project sequence for notification templates: a short, stable, human/SDK
-- friendly id scoped to the project (1, 2, 3, ...). Distinct from the UUID pk,
-- which stays as the internal id. The sequence is assigned on insert by the app
-- (max+1) and guarded by the unique constraint below.
ALTER TABLE notification_templates ADD COLUMN sequence INTEGER;

-- Backfill existing rows ordered by creation within each project.
WITH ranked AS (
  SELECT id,
         ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY created_at, id) AS seq
  FROM notification_templates
)
UPDATE notification_templates t
SET sequence = r.seq
FROM ranked r
WHERE t.id = r.id;

ALTER TABLE notification_templates ALTER COLUMN sequence SET NOT NULL;

ALTER TABLE notification_templates
  ADD CONSTRAINT uk_notification_templates_project_sequence UNIQUE (project_id, sequence);
