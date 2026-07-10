-- V52: app identity for declarative permission sync (spec §18 SDK).
--
-- The permission-declaration endpoint (POST /api/v1/permissions/declare) lets
-- client apps declare the permission keys they use. Without an app identity,
-- a declare call marked as "missing" every CODE/YAML permission absent from its
-- batch — including ones declared by ANOTHER app in the same project. declared_by
-- scopes the missing-marks to the declaring app, so multi-app projects reconcile
-- per-app instead of globally clobbering each other.
ALTER TABLE project_permissions ADD COLUMN declared_by VARCHAR(120);
