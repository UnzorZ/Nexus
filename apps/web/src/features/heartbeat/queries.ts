"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchHeartbeats } from "./api";
import { queryKeys } from "@/lib/api/queryKeys";

/** Refresco en vivo del panel (es de solo lectura, así que es seguro). */
const POLL_INTERVAL_MS = 10_000;

/**
 * Listado de heartbeats de un proyecto (panel, solo lectura) con polling cada
 * {@link POLL_INTERVAL_MS}. El refresco en segundo plano es "silencioso" por
 * defecto en TanStack: no toggla `isLoading` ni limpia los datos ante un fallo
 * transitorio (conserva los últimos) — exactamente la semántica anterior, pero
 * sin el `setInterval` manual.
 */
export function useProjectHeartbeats(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.heartbeats(projectId),
    enabled: !!projectId,
    refetchInterval: POLL_INTERVAL_MS,
    queryFn: () => fetchHeartbeats(projectId),
  });
}
