import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type PermissionSource = "WEB" | "YAML" | "CODE" | "OPENAPI" | "SYSTEM";

export type Permission = {
  id: string;
  key: string;
  label: string;
  description: string | null;
  source: PermissionSource;
};

export async function fetchPermissions(
  projectId: string,
): Promise<Permission[]> {
  return apiClient.get<Permission[]>(
    apiRoutes.panel.projects.permissions.root(projectId),
    { redirect: "manual", errorMessage: "No se pudieron cargar los permisos." },
  );
}

export async function createPermission(
  projectId: string,
  body: { key: string; label: string; description: string | null },
  csrfToken: string,
): Promise<Permission> {
  return apiClient.post<Permission>(
    apiRoutes.panel.projects.permissions.root(projectId),
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo crear el permiso.",
    },
  );
}

export async function updatePermission(
  projectId: string,
  permissionId: string,
  body: { label: string; description: string | null },
  csrfToken: string,
): Promise<Permission> {
  return apiClient.patch<Permission>(
    apiRoutes.panel.projects.permissions.byId(projectId, permissionId),
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo actualizar el permiso.",
    },
  );
}

export async function deletePermission(
  projectId: string,
  permissionId: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.delete<void>(
    apiRoutes.panel.projects.permissions.byId(projectId, permissionId),
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo eliminar el permiso.",
    },
  );
}
