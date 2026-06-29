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
 * Origen ficticio usado únicamente para validar que un path relativo no escapa
 * del origen al resolverlo como un navegador. No se emplea para nada más.
 */
const VALIDATION_ORIGIN = "http://nexus.internal";

/**
 * `true` si el valor es un PATH RELATIVO interno seguro para navegación tras
 * login. Un path relativo no puede cambiar de origen por sí mismo, pero un
 * validador ingenuo (solo "empieza por `/` y no por `//`") deja pasar vectores
 * de open-redirect que el navegador normaliza a un host externo:
 *
 * - `/\evil.com/x`: la barra invertida se normaliza a `/`, así que pasa el
 *   chequeo de `//` pero el navegador lo trata como `//evil.com/x` (host externo).
 * - barras invertidas y caracteres de control que rompen o manipulan el parseo.
 *
 * Por eso, además del prefijo, se rechazan barras invertidas y caracteres de
 * control, y se verifica que resolver el valor contra un origen fijo no dé un
 * origen distinto. La validación de orígenes absolutos del flujo form-login la
 * sigue haciendo el backend (`PanelContinueUrlValidator`).
 */
export function isInternalPath(path: string): boolean {
  if (typeof path !== "string" || path.length === 0) {
    return false;
  }
  // Debe ser relativo a la raíz: nunca absoluto (esquema) ni relativo al esquema.
  if (!path.startsWith("/") || path.startsWith("//")) {
    return false;
  }
  // La barra invertida se normaliza a `/` en los navegadores; rechazarla evita
  // que `/\host` se convierta en `//host` (open-redirect).
  if (path.includes("\\")) {
    return false;
  }
  // Caracteres de control (C0 0x00-0x1f y DEL 0x7f): pueden manipular el parseo
  // o romper cabeceras. Comparacion numerica para no incrustar bytes de control.
  if (Array.from(path).some((ch) => {
    const code = ch.codePointAt(0) as number;
    return code < 0x20 || code === 0x7f;
  })) {
    return false;
  }
  // Garantía final: resolver contra un origen fijo no debe escapar de él.
  try {
    return new URL(path, VALIDATION_ORIGIN).origin === VALIDATION_ORIGIN;
  } catch {
    return false;
  }
}
