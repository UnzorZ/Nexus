import { apiClient, NexusApiError } from "@/lib/api/client";
import { CSRF_HEADER_NAME, ensureCsrfToken } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type CreateNexusAccountPayload = {
  email: string;
  password: string;
  displayName: string;
};

export type NexusAccount = {
  id: string;
  email: string;
  displayName: string;
  status: string;
  mfaEnabled: boolean;
  instanceAdmin: boolean;
  emailVerifiedAt?: string | null;
  lastLoginAt?: string | null;
  createdAt: string;
  updatedAt: string;
};

export async function createNexusAccount(payload: CreateNexusAccountPayload) {
  const csrfToken = await ensureCsrfToken();

  try {
    return await apiClient.post<NexusAccount>(
      apiRoutes.panel.accounts.root,
      payload,
      {
        headers: { [CSRF_HEADER_NAME]: csrfToken },
        errorMessage: "No se pudo crear la cuenta Nexus.",
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
