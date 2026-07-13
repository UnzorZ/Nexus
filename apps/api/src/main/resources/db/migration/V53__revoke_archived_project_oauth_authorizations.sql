-- M3 security remediation: projects archived before the runtime operational
-- gates were introduced may still have refresh-token grants persisted by SAS.
-- Remove only authorizations owned by OAuth clients of archived projects so a
-- later restore cannot revive those historical grants.
--
-- oauth2_authorization.registered_client_id is VARCHAR while
-- project_oauth_clients.id is UUID, hence the explicit CAST.
DELETE FROM oauth2_authorization AS auth
USING project_oauth_clients AS client,
      projects AS project
WHERE auth.registered_client_id = CAST(client.id AS TEXT)
  AND client.project_id = project.id
  AND project.status = 'ARCHIVED';

-- Final persistence barrier for authorization writes. Application-level OAuth
-- guards provide protocol-native errors; this trigger protects the durable sink
-- against races and any future caller that bypasses those guards. FOR SHARE
-- serializes with the project status UPDATE performed by ArchiveProjectService.
CREATE FUNCTION enforce_operational_project_oauth_authorization()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    project_status VARCHAR(32);
    project_client_id UUID;
BEGIN
    -- Global SAS clients use arbitrary identifiers. Parse project-client IDs
    -- once so the lookup keeps the UUID primary-key index usable, and bypass
    -- the project gate when the identifier is not a UUID.
    BEGIN
        project_client_id := NEW.registered_client_id::UUID;
    EXCEPTION
        WHEN invalid_text_representation THEN
            RETURN NEW;
    END;

    SELECT p.status
      INTO project_status
      FROM project_oauth_clients AS c
      JOIN projects AS p ON p.id = c.project_id
     WHERE c.id = project_client_id
       FOR SHARE OF p;

    IF project_status IS NOT NULL AND project_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Cannot persist OAuth authorization for a non-operational project'
            USING ERRCODE = '23514',
                  CONSTRAINT = 'ck_oauth_authorization_operational_project';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_oauth_authorization_operational_project
BEFORE INSERT OR UPDATE ON oauth2_authorization
FOR EACH ROW
EXECUTE FUNCTION enforce_operational_project_oauth_authorization();
