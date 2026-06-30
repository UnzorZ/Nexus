import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type Role = {
  id: string;
  key: string;
  label: string;
  description: string | null;
  system: boolean;
  permissionKeys: string[];
};

/**
 * Validador cliente de claves de permiso, espejo del `@PermissionKey` del
 * backend: comodín global `*`, comodín de espacio `ns.*` o claves exactas de
 * segmentos `[a-z0-9_-]+`.
 */
const PERMISSION_KEY_RE = /^(\*|[a-z0-9_-]+(\.[a-z0-9_-]+)*(\.\*)?)$/;

export function isValidPermissionKey(key: string): boolean {
  return PERMISSION_KEY_RE.test(key);
}

export async function fetchRoles(projectId: string): Promise<Role[]> {
  return apiClient.get<Role[]>(apiRoutes.panel.projects.roles.root(projectId), {
    redirect: "manual",
    errorMessage: "No se pudieron cargar los roles.",
  });
}

export async function createRole(
  projectId: string,
  body: { key: string; label: string; description: string | null },
  csrfToken: string,
): Promise<Role> {
  return apiClient.post<Role>(
    apiRoutes.panel.projects.roles.root(projectId),
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo crear el rol.",
    },
  );
}

export async function updateRole(
  projectId: string,
  roleId: string,
  body: { label: string; description: string | null },
  csrfToken: string,
): Promise<Role> {
  return apiClient.patch<Role>(
    apiRoutes.panel.projects.roles.byId(projectId, roleId),
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo actualizar el rol.",
    },
  );
}

export async function deleteRole(
  projectId: string,
  roleId: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.delete<void>(
    apiRoutes.panel.projects.roles.byId(projectId, roleId),
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo eliminar el rol.",
    },
  );
}

/** Reemplazo completo (PUT) de las claves de permiso asignadas al rol. */
export async function setRolePermissions(
  projectId: string,
  roleId: string,
  permissionKeys: string[],
  csrfToken: string,
): Promise<Role> {
  return apiClient.put<Role>(
    apiRoutes.panel.projects.roles.permissions(projectId, roleId),
    { permissionKeys },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudieron actualizar los permisos del rol.",
    },
  );
}
