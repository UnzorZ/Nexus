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
