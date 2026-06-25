/**
 * Construye la URL de inicio de sesión del panel con un parámetro `continue`
 * que siempre es un PATH RELATIVO interno.
 *
 * Un path relativo nunca es vector de open-redirect: no puede salir del origen
 * con el que navega el usuario. Por eso la validación de seguridad del
 * `continue` la sigue haciendo el backend (`PanelContinueUrlValidator`) en el
 * flujo de form-login directo, y este módulo no reimplementa la validación de
 * orígenes que antes vivía en `lib/api/routes.ts`.
 */
export function buildPanelLoginUrl(continuePath = "/projects"): string {
  const safe = isInternalPath(continuePath) ? continuePath : "/projects";
  return `/login?continue=${encodeURIComponent(safe)}`;
}

/**
 * `true` si el valor es un path interno: empieza por `/` pero no por `//`
 * (una cadena que empieza por `//` se interpretaría como URL relativa al
 * esquema, p. ej. `//evil.com`).
 */
export function isInternalPath(path: string): boolean {
  return typeof path === "string" && path.startsWith("/") && !path.startsWith("//");
}
