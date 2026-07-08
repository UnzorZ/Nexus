import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type OauthClientStatus = "ACTIVE" | "DISABLED";

/**
 * Cliente OAuth/OIDC de un proyecto (issuer {origin}/p/{slug}). No expone el
 * hash del secreto; {@code confidential} indica si el cliente tiene secreto.
 */
export type OauthClientSummary = {
  id: string;
  clientId: string;
  name: string;
  redirectUris: string[];
  postLogoutRedirectUris: string[];
  grantTypes: string[];
  scopes: string[];
  requirePkce: boolean;
  consentRequired: boolean;
  confidential: boolean;
  status: OauthClientStatus;
  createdAt: string;
  updatedAt: string;
};

/** Respuesta de crear/rotar: {@code clientSecret} se devuelve una sola vez
 *  (null para clientes públicos sin secreto). */
export type OauthClientCreated = OauthClientSummary & {
  clientSecret: string | null;
};

export type CreateOauthClientPayload = {
  name: string;
  redirectUris: string[];
  postLogoutRedirectUris?: string[];
  grantTypes?: string[];
  scopes?: string[];
  requirePkce: boolean;
  confidential: boolean;
  consentRequired: boolean;
};

export async function fetchOauthClients(
  projectId: string,
): Promise<OauthClientSummary[]> {
  return apiClient.get<OauthClientSummary[]>(
    apiRoutes.panel.projects.oauthClients.root(projectId),
    {
      redirect: "manual",
      errorMessage: "No se pudieron cargar los clientes OAuth.",
    },
  );
}

export async function createOauthClient(
  projectId: string,
  payload: CreateOauthClientPayload,
  csrfToken: string,
): Promise<OauthClientCreated> {
  return apiClient.post<OauthClientCreated>(
    apiRoutes.panel.projects.oauthClients.root(projectId),
    payload,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo crear el cliente OAuth.",
    },
  );
}

export async function rotateOauthClient(
  projectId: string,
  id: string,
  csrfToken: string,
): Promise<OauthClientCreated> {
  return apiClient.post<OauthClientCreated>(
    apiRoutes.panel.projects.oauthClients.rotate(projectId, id),
    null,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo rotar el secreto.",
    },
  );
}

export async function disableOauthClient(
  projectId: string,
  id: string,
  csrfToken: string,
): Promise<OauthClientSummary> {
  return apiClient.post<OauthClientSummary>(
    apiRoutes.panel.projects.oauthClients.disable(projectId, id),
    null,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo desactivar el cliente OAuth.",
    },
  );
}

export async function deleteOauthClient(
  projectId: string,
  id: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.delete<null>(
    apiRoutes.panel.projects.oauthClients.byId(projectId, id),
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo eliminar el cliente OAuth.",
    },
  );
}
