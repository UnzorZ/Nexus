-- Asignaciones usuario-de-proyecto → rol (spec §9.8). Un ProjectUser puede tener
-- cero o más roles; las authorities efectivas son la unión de las claves de
-- permiso de esos roles. Espeja project_role_permissions: semántica de
-- reemplazo completo (PUT) con borrado bulk previo a la reinserción.
--
-- project_user_id es un UUID tipado SIN FK a identity.project_users, por la
-- regla cross-module de AGENTS.md (un módulo nunca hace FK a tablas de otro):
-- un ProjectUser borrado deja huérfanas que se toleran (no se reconsultan y los
-- UUID no se reutilizan). role_id SÍ hace FK→project_roles con ON DELETE
-- CASCADE, igual que project_role_permissions: borrar un rol elimina sus
-- asignaciones atómicamente.

CREATE TABLE project_user_roles (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    project_user_id UUID NOT NULL,
    role_id UUID NOT NULL REFERENCES project_roles (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_project_user_roles_user_role
        UNIQUE (project_user_id, role_id)
);

CREATE INDEX ix_project_user_roles_project_user
    ON project_user_roles (project_id, project_user_id);
CREATE INDEX ix_project_user_roles_role
    ON project_user_roles (project_id, role_id);
