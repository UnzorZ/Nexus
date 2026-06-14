import { apiClient, NexusApiError } from "@/lib/api/client";
import { apiRoutes } from "@/lib/api/routes";

export const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

const CSRF_COOKIE_NAME = "XSRF-TOKEN";

function readCookie(name: string) {
  if (typeof document === "undefined") {
    return null;
  }

  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

export async function ensureCsrfToken() {
  await apiClient.get<null>(apiRoutes.panel.session.csrf, {
    errorMessage: "No se pudo inicializar la protección CSRF.",
  });

  const token = readCookie(CSRF_COOKIE_NAME);
  if (!token) {
    throw new NexusApiError(
      "La cookie CSRF no está disponible. Recarga la página e inténtalo de nuevo.",
      { status: 403, code: "csrf_missing" },
    );
  }

  return token;
}
