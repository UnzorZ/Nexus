import { apiClient, NexusApiError } from "@/lib/api/client";
import { apiRoutes } from "@/lib/api/routes";

export const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

/**
 * Obtiene el token CSRF del panel.
 *
 * Lo lee del CUERPO de `GET /csrf` (no de `document.cookie`): cuando el
 * frontend y el API están en orígenes distintos (p. ej. distintos subdominios
 * de ngrok), la cookie `XSRF-TOKEN` la emite el host del API y el JS del
 * frontend no puede leerla. El backend devuelve el token en el cuerpo para
 * exactamente este caso; la cookie sigue emitiéndose y viaja en las
 * escrituras con credenciales para el double-submit.
 */
export async function ensureCsrfToken() {
  const body = await apiClient.get<{ token?: string } | null>(
    apiRoutes.panel.session.csrf,
    {
      errorMessage: "No se pudo inicializar la protección CSRF.",
    },
  );

  const token = body?.token;
  if (!token) {
    throw new NexusApiError(
      "El token CSRF no está disponible. Recarga la página e inténtalo de nuevo.",
      { status: 403, code: "csrf_missing" },
    );
  }

  return token;
}
