import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";
import type { Role } from "@/features/roles/api";

export type { Role };

/** Roles asignados a un usuario de proyecto (lectura, panel). */
export async function fetchUserRoles(
  projectId: string,
  userId: string,
): Promise<Role[]> {
  return apiClient.get<Role[]>(
    apiRoutes.panel.projects.users.roles(projectId, userId),
    {
      redirect: "manual",
      errorMessage: "No se pudieron cargar los roles del usuario.",
    },
  );
}

/** Reemplazo completo (PUT) de los roles asignados al usuario. */
export async function setUserRoles(
  projectId: string,
  userId: string,
  roleIds: string[],
  csrfToken: string,
): Promise<Role[]> {
  return apiClient.put<Role[]>(
    apiRoutes.panel.projects.users.roles(projectId, userId),
    { roleIds },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudieron actualizar los roles del usuario.",
    },
  );
}
