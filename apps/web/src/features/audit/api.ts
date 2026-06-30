import { apiClient } from "@/lib/api/client";
import { apiRoutes } from "@/lib/api/routes";

/** Resultado de un evento auditado (server-side, enum name como string). */
export type AuditOutcome = "SUCCESS" | "FAILURE";

/**
 * Vista de un evento de auditoría para el panel (ADR-0004). El `outcome` es el
 * nombre del enum; el `actorType` es libre (hoy `NEXUS_ACCOUNT` / `ANONYMOUS`).
 * La metadata nunca lleva secretos. `projectId` es nullable (rechazos anónimos).
 */
export type AuditEvent = {
  id: string;
  projectId: string | null;
  action: string;
  resourceType: string | null;
  resourceId: string | null;
  outcome: AuditOutcome;
  actorType: string;
  actorId: string | null;
  actorDisplayName: string | null;
  actorEmail: string | null;
  ip: string | null;
  userAgent: string | null;
  traceId: string | null;
  occurredAt: string;
  metadata: Record<string, unknown> | null;
};

export type AuditQuery = {
  /** Acota a eventos desde este instante ISO (lo usa el filtro de rango). */
  since?: string;
};

/** Listado de eventos de auditoría de un proyecto (panel, solo lectura). */
export async function fetchProjectAudit(
  projectId: string,
  query: AuditQuery = {},
): Promise<AuditEvent[]> {
  const url = new URL(apiRoutes.panel.projects.audit.root(projectId));
  if (query.since) url.searchParams.set("since", query.since);
  return apiClient.get<AuditEvent[]>(url.toString(), {
    redirect: "manual",
    errorMessage: "No se pudo cargar el log de auditoría.",
  });
}
