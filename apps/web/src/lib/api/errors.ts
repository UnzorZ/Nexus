import { NexusApiError } from "@/lib/api/client";

export type ErrorMessages = {
  /** 403 `permission_denied` (requireManage/requireDelete falló). */
  permission?: string;
  /** 403 `module_disabled` (el proyecto tiene este módulo apagado). */
  moduleDisabled?: string;
  /** 403 genérico (sesión caducada / CSRF). */
  forbidden?: string;
  /** 404 no encontrado. */
  notFound?: string;
  /** Sobrescrituras extra por `code` (p. ej. { last_owner: "..." }). */
  codes?: Record<string, string>;
};

const DEFAULT_PERMISSION = "You don't have permission to do that.";
const DEFAULT_MODULE_DISABLED =
  "This module is disabled for this project. Re-enable it from Modules.";
const DEFAULT_FORBIDDEN = "Your session expired. Reload the page and try again.";
const DEFAULT_NOT_FOUND = "This resource no longer exists.";

/**
 * Divide el `detail` de un `validation_error` ("campo: msg; campo2: msg2") en
 * un mapa campo → mensaje. Centralizado (antes vivía en features/projects/api)
 * para que lo reutilicen los hooks y las páginas.
 */
export function parseFieldErrors(detail: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const part of detail.split(/\s*;\s*/)) {
    const match = part.match(/^(\w+):\s*(.+)$/);
    if (match) {
      out[match[1]] = match[2];
    }
  }
  return out;
}

/**
 * Extrae errores por campo de una respuesta `400 validation_error`. Devuelve
 * `{}` para cualquier otro error (la página lo trata como error genérico vía
 * {@link toMessage}).
 */
export function toFieldErrors(err: unknown): Record<string, string> {
  if (!(err instanceof NexusApiError)) return {};
  if (err.status !== 400 || err.code !== "validation_error") return {};
  return parseFieldErrors(err.message);
}

/**
 * Convierte un error lanzado por el cliente de API (típicamente
 * {@link NexusApiError}) en un único mensaje para el usuario, siguiendo el
 * escalafón `status`+`code` común a todos los módulos. Pasa `messages` para
 * matizar la frase según el recurso de la página.
 */
export function toMessage(err: unknown, messages: ErrorMessages = {}): string {
  if (!(err instanceof NexusApiError)) {
    return "Something went wrong.";
  }
  if (err.code && messages.codes?.[err.code]) {
    return messages.codes[err.code];
  }
  if (err.status === 403 && err.code === "permission_denied") {
    return messages.permission ?? DEFAULT_PERMISSION;
  }
  if (err.status === 403 && err.code === "module_disabled") {
    return messages.moduleDisabled ?? DEFAULT_MODULE_DISABLED;
  }
  if (err.status === 403) {
    return messages.forbidden ?? DEFAULT_FORBIDDEN;
  }
  if (err.status === 404) {
    return messages.notFound ?? DEFAULT_NOT_FOUND;
  }
  return err.message;
}
