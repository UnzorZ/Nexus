"use client";

import { useCallback, useEffect, useState } from "react";
import { NexusApiError } from "@/lib/api/client";
import { fetchProjectAudit, type AuditEvent } from "./api";

export type UseProjectAuditResult = {
  events: AuditEvent[] | null;
  loading: boolean;
  error: string | null;
  refresh: () => void;
};

/**
 * Carga el log de auditoría de un proyecto (panel, solo lectura). Sin polling:
 * la auditoría es de baja frecuencia, así que se refresca a mano (botón Retry).
 * `rangeMs` acota la ventana temporal (`since` en el servidor); se calcula el
 * instante dentro de `load` (async, fuera del render) para no llamar a
 * `Date.now()` durante el render. `load` depende solo de primitivos estables.
 */
export function useProjectAudit(
  projectId: string,
  rangeMs = 0,
): UseProjectAuditResult {
  const [events, setEvents] = useState<AuditEvent[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!projectId) {
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      const since =
        rangeMs > 0 ? new Date(Date.now() - rangeMs).toISOString() : undefined;
      setEvents(await fetchProjectAudit(projectId, since ? { since } : {}));
      setError(null);
    } catch (err) {
      setError(
        err instanceof NexusApiError
          ? err.message
          : "No se pudo cargar el log de auditoría.",
      );
    } finally {
      setLoading(false);
    }
  }, [projectId, rangeMs]);

  useEffect(() => {
    // Data fetch on mount: react-hooks/set-state-in-effect is overly strict for
    // fetching (the established pattern across the codebase, e.g. useProjectModules,
    // ProjectProvider). Broader repo lint debt is pre-existing and out of scope here.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  return { events, loading, error, refresh: () => load() };
}
