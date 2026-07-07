import { apiClient, NexusApiError } from "@/lib/api/client";
import { apiRoutes } from "@/lib/api/routes";

export const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

/**
 * Obtiene el token CSRF de una cadena de seguridad.
 *
 * Lo lee del CUERPO de `GET <csrfUrl>` (no de `document.cookie`): cuando el frontend y
 * el API están en orígenes distintos, la cookie `XSRF-TOKEN` la emite el host del API y el
 * JS del frontend no puede leerla. El backend devuelve el token en el cuerpo para
 * exactamente este caso; la cookie sigue emitiéndose y viaja en las escrituras con
 * credenciales para el double-submit.
 *
 * Por defecto usa el endpoint CSRF del panel; las páginas de usuario final pasan su propia
 * ruta (`apiRoutes.endUser.session.csrf`) para obtener un token de la cadena `/api/p/**`.
 */
export async function ensureCsrfToken(csrfUrl: string = apiRoutes.panel.session.csrf) {
  const body = await apiClient.get<{ token?: string } | null>(csrfUrl, {
    errorMessage: "No se pudo inicializar la protección CSRF.",
  });

  const token = body?.token;
  if (!token) {
    throw new NexusApiError(
      "El token CSRF no está disponible. Recarga la página e inténtalo de nuevo.",
      { status: 403, code: "csrf_missing" },
    );
  }

  return token;
}

/**
 * Envuelve una mutación que necesita token CSRF: obtiene el token una vez y lo
 * pasa al cuerpo de la mutación. Pensado para el `mutationFn` de `useMutation`,
 * donde el caller ya no gestiona el token manualmente:
 *
 *     mutationFn: (payload) => withCsrf((token) => inviteMember(id, payload, token))
 */
export async function withCsrf<T>(
  run: (token: string) => Promise<T>,
  csrfUrl: string = apiRoutes.panel.session.csrf,
): Promise<T> {
  const token = await ensureCsrfToken(csrfUrl);
  return run(token);
}
