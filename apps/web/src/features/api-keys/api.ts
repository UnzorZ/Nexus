import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type ApiKeyStatus = "ACTIVE" | "DISABLED";

export type ApiKeySummary = {
  id: string;
  name: string;
  keyPrefix: string;
  status: ApiKeyStatus;
  scopes: string[];
  expiresAt: string | null;
  lastUsedAt: string | null;
  createdAt: string;
};

export type ApiKeyCreated = {
  summary: ApiKeySummary;
  key: string;
};

/**
 * Scopes documentados (spec §12.2) que se ofrecen como sugerencias al crear una
 * key. Se permiten scopes custom (validados por formato en el backend).
 */
export const KNOWN_SCOPES = [
  "registry:heartbeat",
  "permissions:declare",
  "permissions:check",
  "authz:snapshot",
  "notify:send",
];

const SCOPE_RE = /^[a-z][a-z0-9-]*:[a-z][a-z0-9_-]*$/;

export function isValidScope(scope: string): boolean {
  return SCOPE_RE.test(scope);
}

export async function fetchApiKeys(
  projectId: string,
): Promise<ApiKeySummary[]> {
  return apiClient.get<ApiKeySummary[]>(
    apiRoutes.panel.projects.apiKeys.root(projectId),
    { redirect: "manual", errorMessage: "No se pudieron cargar las API keys." },
  );
}

export async function createApiKey(
  projectId: string,
  body: { name: string; scopes: string[]; expiresAt: string | null },
  csrfToken: string,
): Promise<ApiKeyCreated> {
  return apiClient.post<ApiKeyCreated>(
    apiRoutes.panel.projects.apiKeys.root(projectId),
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo crear la API key.",
    },
  );
}

export async function updateApiKey(
  projectId: string,
  keyId: string,
  body: { name: string; status: ApiKeyStatus; expiresAt: string | null },
  csrfToken: string,
): Promise<ApiKeySummary> {
  return apiClient.patch<ApiKeySummary>(
    apiRoutes.panel.projects.apiKeys.byId(projectId, keyId),
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo actualizar la API key.",
    },
  );
}

export async function rotateApiKey(
  projectId: string,
  keyId: string,
  csrfToken: string,
): Promise<ApiKeyCreated> {
  return apiClient.post<ApiKeyCreated>(
    apiRoutes.panel.projects.apiKeys.rotate(projectId, keyId),
    undefined,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo rotar la API key.",
    },
  );
}

export async function deleteApiKey(
  projectId: string,
  keyId: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.delete<void>(
    apiRoutes.panel.projects.apiKeys.byId(projectId, keyId),
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo eliminar la API key.",
    },
  );
}
