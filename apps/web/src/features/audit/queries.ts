"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchProjectAudit } from "./api";
import { queryKeys } from "@/lib/api/queryKeys";

/**
 * Log de auditoría de un proyecto (panel, solo lectura). Sin polling: la
 * auditoría es de baja frecuencia → refresco manual (Retry/Refresh).
 *
 * `rangeMs` acota la ventana temporal (`since` en el servidor) y se calcula el
 * instante DENTRO del `queryFn` (async) para no llamar a `Date.now()` durante el
 * render ni meterlo en la query key (pureza del React Compiler).
 */
export function useProjectAudit(projectId: string, rangeMs = 0) {
  return useQuery({
    queryKey: queryKeys.projects.audit(projectId, rangeMs),
    enabled: !!projectId,
    queryFn: async () => {
      const since =
        rangeMs > 0 ? new Date(Date.now() - rangeMs).toISOString() : undefined;
      return fetchProjectAudit(projectId, since ? { since } : {});
    },
  });
}
