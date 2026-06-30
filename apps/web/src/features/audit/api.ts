import { apiClient } from "@/lib/api/client";
import { apiRoutes } from "@/lib/api/routes";

/** Severidad de un evento (server-side, enum name como string). */
export type Severity = "INFO" | "WARNING" | "MODERATE" | "CRITICAL";

/**
 * Vista de un evento de auditoría para el panel (ADR-0004). La `severity` es el
 * nombre del enum; el `actorType` es libre (hoy `NEXUS_ACCOUNT` / `ANONYMOUS`,
 * y en el futuro cuentas OAuth). La metadata nunca lleva secretos. `projectId`
 * es nullable (rechazos anónimos).
 */
export type AuditEvent = {
  id: string;
  projectId: string | null;
  action: string;
  resourceType: string | null;
  resourceId: string | null;
  severity: Severity;
  actorType: string;
  actorId: string | null;
  actorDisplayName: string | null;
  actorEmail: string | null;
  actorAdmin: boolean | null;
  ip: string | null;
  userAgent: string | null;
  traceId: string | null;
  occurredAt: string;
  metadata: Record<string, unknown> | null;
};

export type AuditQuery = {
  /** Acota a eventos desde este instante ISO (lo usa el filtro de rango). */
  since?: string;
  /** Página (0-based) y tamaño para la paginación backend. */
  page?: number;
  size?: number;
};

/** Página de auditoría devuelta por el backend (forma estable). */
export type AuditPage = {
  items: AuditEvent[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  last: boolean;
};

/** Página de eventos de auditoría de un proyecto (panel, solo lectura). */
export async function fetchProjectAudit(
  projectId: string,
  query: AuditQuery = {},
): Promise<AuditPage> {
  const url = new URL(apiRoutes.panel.projects.audit.root(projectId));
  if (query.since) url.searchParams.set("since", query.since);
  if (query.page != null) url.searchParams.set("page", String(query.page));
  if (query.size != null) url.searchParams.set("size", String(query.size));
  return apiClient.get<AuditPage>(url.toString(), {
    redirect: "manual",
    errorMessage: "No se pudo cargar el log de auditoría.",
  });
}
