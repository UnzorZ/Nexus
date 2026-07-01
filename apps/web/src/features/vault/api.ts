import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type Secret = {
  id: string;
  key: string;
  createdAt: string;
  updatedAt: string;
  lastRotatedAt: string | null;
};

/** El valor plano nunca se devuelve desde el panel. */
export async function fetchSecrets(projectId: string): Promise<Secret[]> {
  return apiClient.get<Secret[]>(
    apiRoutes.panel.projects.vault.secretsRoot(projectId),
    { redirect: "manual", errorMessage: "No se pudieron cargar los secretos." },
  );
}

export async function createSecret(
  projectId: string,
  key: string,
  value: string,
  csrfToken: string,
): Promise<Secret> {
  return apiClient.post<Secret>(
    apiRoutes.panel.projects.vault.secretByKey(projectId, key),
    { value },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo crear el secreto.",
    },
  );
}

export async function rotateSecret(
  projectId: string,
  key: string,
  value: string,
  csrfToken: string,
): Promise<Secret> {
  return apiClient.patch<Secret>(
    apiRoutes.panel.projects.vault.secretByKey(projectId, key),
    { value },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo rotar el secreto.",
    },
  );
}

export async function deleteSecret(
  projectId: string,
  key: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.delete<void>(
    apiRoutes.panel.projects.vault.secretByKey(projectId, key),
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo eliminar el secreto.",
    },
  );
}
