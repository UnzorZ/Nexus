import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type ProjectUserStatus =
  | "PENDING_VERIFICATION"
  | "ACTIVE"
  | "SUSPENDED"
  | "DISABLED";

/**
 * Usuario final de un proyecto (su realm OAuth/OIDC). Distinto de un NexusAccount
 * (miembro del panel): no expone passwordHash.
 */
export type ProjectUser = {
  id: string;
  email: string;
  username: string | null;
  displayName: string;
  status: ProjectUserStatus;
  emailVerifiedAt: string | null;
  lastLoginAt: string | null;
  createdAt: string;
};

export type CreateProjectUserPayload = {
  email: string;
  username?: string | null;
  displayName: string;
  password: string;
};

export type UpdateProjectUserPayload = {
  displayName: string;
  username?: string | null;
};

export async function fetchProjectUsers(
  projectId: string,
): Promise<ProjectUser[]> {
  return apiClient.get<ProjectUser[]>(
    apiRoutes.panel.projects.users.root(projectId),
    {
      redirect: "manual",
      errorMessage: "No se pudieron cargar los usuarios.",
    },
  );
}

export async function createProjectUser(
  projectId: string,
  payload: CreateProjectUserPayload,
  csrfToken: string,
): Promise<ProjectUser> {
  return apiClient.post<ProjectUser>(
    apiRoutes.panel.projects.users.root(projectId),
    payload,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo crear el usuario.",
    },
  );
}

export async function updateProjectUser(
  projectId: string,
  userId: string,
  payload: UpdateProjectUserPayload,
  csrfToken: string,
): Promise<ProjectUser> {
  return apiClient.patch<ProjectUser>(
    apiRoutes.panel.projects.users.byId(projectId, userId),
    payload,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo actualizar el usuario.",
    },
  );
}

export async function suspendProjectUser(
  projectId: string,
  userId: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.post<null>(
    apiRoutes.panel.projects.users.statusAction(projectId, userId, "suspend"),
    null,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo suspender al usuario.",
    },
  );
}

export async function reactivateProjectUser(
  projectId: string,
  userId: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.post<null>(
    apiRoutes.panel.projects.users.statusAction(projectId, userId, "reactivate"),
    null,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo reactivar al usuario.",
    },
  );
}

export async function disableProjectUser(
  projectId: string,
  userId: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.post<null>(
    apiRoutes.panel.projects.users.statusAction(projectId, userId, "disable"),
    null,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo desactivar al usuario.",
    },
  );
}

export async function deleteProjectUser(
  projectId: string,
  userId: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.delete<null>(
    apiRoutes.panel.projects.users.byId(projectId, userId),
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo eliminar al usuario.",
    },
  );
}

export async function resetProjectUserPassword(
  projectId: string,
  userId: string,
  newPassword: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.post<null>(
    apiRoutes.panel.projects.users.resetPassword(projectId, userId),
    { newPassword },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo restablecer la contraseña.",
    },
  );
}
