-- Remove the documents module (dropped from the app and spec). Drops the
-- document tables (V14) and the now-obsolete per-project module rows, so loading
-- a project's modules doesn't hit a missing enum constant (Hibernate maps
-- project_modules.module as @Enumerated(STRING) → 'DOCUMENTS').
DROP TABLE IF EXISTS document_renders;
DROP TABLE IF EXISTS document_templates;
DELETE FROM project_modules WHERE module = 'DOCUMENTS';
