import { apiClient, NexusApiError } from "@/lib/api/client";
import { CSRF_HEADER_NAME, ensureCsrfToken } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type PanelMfaEnrollment = {
  secret: string;
  otpauthUri: string;
};

export type PanelMfaStatus = {
  enabled: boolean;
  recoveryCodesRemaining: number;
};

/**
 * Completa el login MFA del panel con un código TOTP (o recovery). Devuelve la cuenta si
 * ok; el controlador responde 401 {code: invalid_code} si el código no valida.
 */
export async function completePanelMfaLogin(code: string): Promise<unknown> {
  const csrfToken = await ensureCsrfToken();
  return apiClient.post<unknown>(
    apiRoutes.panel.session.loginMfa,
    { code },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "Código incorrecto.",
    },
  );
}

export async function fetchPanelMfaStatus(): Promise<PanelMfaStatus> {
  return apiClient.get<PanelMfaStatus>(apiRoutes.panel.mfa.status, {
    redirect: "manual",
    errorMessage: "No se pudo cargar el estado MFA.",
  });
}

export async function beginPanelMfaEnrollment(): Promise<PanelMfaEnrollment> {
  const csrfToken = await ensureCsrfToken();
  return apiClient.post<PanelMfaEnrollment>(apiRoutes.panel.mfa.enroll, undefined, {
    headers: { [CSRF_HEADER_NAME]: csrfToken },
    redirect: "manual",
    errorMessage: "No se pudo iniciar la inscripción MFA.",
  });
}

export async function confirmPanelMfaEnrollment(code: string): Promise<string[]> {
  const csrfToken = await ensureCsrfToken();
  const result = await apiClient.post<{ recoveryCodes: string[] }>(
    apiRoutes.panel.mfa.enrollVerify,
    { code },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "Código incorrecto.",
    },
  );
  return result.recoveryCodes;
}

export async function disablePanelMfa(code: string): Promise<void> {
  const csrfToken = await ensureCsrfToken();
  try {
    await apiClient.post<null>(apiRoutes.panel.mfa.disable, { code }, {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo desactivar la MFA.",
    });
  } catch (error) {
    if (error instanceof NexusApiError && (error.status === 400 || error.status === 409)) {
      throw new NexusApiError("Código incorrecto.", {
        status: error.status,
        code: "invalid_code",
      });
    }
    throw error;
  }
}
