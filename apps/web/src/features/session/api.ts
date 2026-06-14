import { apiClient, NexusApiError } from "@/lib/api/client";
import { CSRF_HEADER_NAME, ensureCsrfToken } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";
import type { NexusAccount } from "@/features/accounts/api";

export async function fetchCurrentAccount(): Promise<NexusAccount | null> {
  try {
    return await apiClient.get<NexusAccount>(apiRoutes.panel.session.me, {
      redirect: "manual",
      errorMessage: "No se pudo comprobar la sesión del panel.",
    });
  } catch (error) {
    if (error instanceof NexusApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}

export async function logoutPanelSession() {
  const csrfToken = await ensureCsrfToken();

  await apiClient.post<null>(apiRoutes.panel.session.logout, undefined, {
    headers: { [CSRF_HEADER_NAME]: csrfToken },
    redirect: "manual",
    errorMessage: "No se pudo cerrar la sesión.",
  });
}

export type PanelSessionSummary = {
  id: string;
  current: boolean;
  userAgent: string;
  createdAt: string;
  lastAccessedAt: string;
  expiresAt: string;
  maxInactiveIntervalSeconds: number;
};

export async function fetchPanelSessions(): Promise<PanelSessionSummary[]> {
  return apiClient.get<PanelSessionSummary[]>(apiRoutes.panel.sessions.root, {
    redirect: "manual",
    errorMessage: "No se pudieron cargar las sesiones.",
  });
}

export async function revokePanelSession(publicSessionId: string) {
  const csrfToken = await ensureCsrfToken();

  try {
    await apiClient.delete<null>(
      apiRoutes.panel.sessions.byId(publicSessionId),
      {
        headers: { [CSRF_HEADER_NAME]: csrfToken },
        redirect: "manual",
        errorMessage: "No se pudo revocar la sesión.",
      },
    );
  } catch (error) {
    if (error instanceof NexusApiError && error.status === 403) {
      throw new NexusApiError(
        "El formulario expiró o falta protección CSRF. Recarga e inténtalo de nuevo.",
        { status: error.status, code: "csrf_rejected", data: error.data },
      );
    }
    throw error;
  }
}

export async function revokeAllPanelSessions() {
  const csrfToken = await ensureCsrfToken();

  try {
    await apiClient.delete<null>(apiRoutes.panel.sessions.root, {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudieron cerrar todas las sesiones.",
    });
  } catch (error) {
    if (error instanceof NexusApiError && error.status === 403) {
      throw new NexusApiError(
        "El formulario expiró o falta protección CSRF. Recarga e inténtalo de nuevo.",
        { status: error.status, code: "csrf_rejected", data: error.data },
      );
    }
    throw error;
  }
}
