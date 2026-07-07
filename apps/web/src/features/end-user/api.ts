import { apiClient, NexusApiError } from "@/lib/api/client";
import { CSRF_HEADER_NAME, ensureCsrfToken } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

/** Usuario final autenticado de un proyecto (shape de `GET /api/p/{slug}/me`). */
export type ProjectUserDetails = {
  id: string;
  email: string;
  username: string | null;
  displayName: string | null;
  status: string;
  emailVerifiedAt: string | null;
  lastLoginAt: string | null;
  createdAt: string;
};

export type EndUserLoginResult = {
  /** URL absoluta del API a la que navegar tras el login (reanuda /oauth2/authorize). */
  redirect?: string;
};

/**
 * Devuelve el usuario final autenticado, o `null` si no hay sesión (la API resuelve el
 * 401 a null aquí). Usado por el guard de las páginas /account de usuario final.
 */
export async function fetchEndUserMe(
  projectSlug: string,
): Promise<ProjectUserDetails | null> {
  try {
    return await apiClient.get<ProjectUserDetails>(
      apiRoutes.endUser.session.me(projectSlug),
      { redirect: "manual", errorMessage: "No se pudo comprobar la sesión." },
    );
  } catch (error) {
    if (error instanceof NexusApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}

export async function loginEndUser(
  projectSlug: string,
  credentials: { email: string; password: string; continueUrl?: string },
): Promise<EndUserLoginResult> {
  const csrfToken = await ensureCsrfToken(
    apiRoutes.endUser.session.csrf(projectSlug),
  );
  return apiClient.post<EndUserLoginResult>(
    apiRoutes.endUser.session.login(projectSlug),
    {
      email: credentials.email,
      password: credentials.password,
      continueUrl: credentials.continueUrl,
    },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "Email o contraseña incorrectos.",
    },
  );
}

export async function logoutEndUser(projectSlug: string): Promise<void> {
  const csrfToken = await ensureCsrfToken(
    apiRoutes.endUser.session.csrf(projectSlug),
  );
  await apiClient.post<null>(
    apiRoutes.endUser.session.logout(projectSlug),
    undefined,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo cerrar la sesión.",
    },
  );
}

export async function registerEndUser(
  projectSlug: string,
  payload: { email: string; password: string; displayName?: string; username?: string },
): Promise<void> {
  const csrfToken = await ensureCsrfToken(
    apiRoutes.endUser.session.csrf(projectSlug),
  );
  await apiClient.post<unknown>(apiRoutes.endUser.register(projectSlug), payload, {
    headers: { [CSRF_HEADER_NAME]: csrfToken },
    redirect: "manual",
    errorMessage: "No se pudo completar el registro.",
  });
}

export async function verifyEndUserEmail(
  projectSlug: string,
  token: string,
): Promise<void> {
  const csrfToken = await ensureCsrfToken(
    apiRoutes.endUser.session.csrf(projectSlug),
  );
  await apiClient.post<unknown>(
    apiRoutes.endUser.verifyEmail(projectSlug),
    { token },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "El enlace de verificación no es válido o ha caducado.",
    },
  );
}

export async function resendEndUserVerification(
  projectSlug: string,
  email: string,
): Promise<void> {
  const csrfToken = await ensureCsrfToken(
    apiRoutes.endUser.session.csrf(projectSlug),
  );
  await apiClient.post<unknown>(
    apiRoutes.endUser.resendVerification(projectSlug),
    { email },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo reenviar el enlace.",
    },
  );
}

export async function requestEndUserPasswordReset(
  projectSlug: string,
  email: string,
): Promise<void> {
  const csrfToken = await ensureCsrfToken(
    apiRoutes.endUser.session.csrf(projectSlug),
  );
  // Anti-enumeración: la API responde siempre 200 exista o no la cuenta.
  await apiClient.post<unknown>(
    apiRoutes.endUser.passwordReset(projectSlug),
    { email },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo enviar el enlace de reseteo.",
    },
  );
}

export async function confirmEndUserPasswordReset(
  projectSlug: string,
  token: string,
  newPassword: string,
): Promise<void> {
  const csrfToken = await ensureCsrfToken(
    apiRoutes.endUser.session.csrf(projectSlug),
  );
  await apiClient.post<unknown>(
    apiRoutes.endUser.passwordResetConfirm(projectSlug),
    { token, newPassword },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo actualizar la contraseña.",
    },
  );
}
