-- Drop the now-unused stale_after_seconds column. Liveness uses interval +
-- timeout only (ONLINE within interval, STALE until timeout, OFFLINE after);
-- staleAfter no longer marked a state boundary. Introduced in V20.
ALTER TABLE project_registry_settings DROP COLUMN IF EXISTS stale_after_seconds;
