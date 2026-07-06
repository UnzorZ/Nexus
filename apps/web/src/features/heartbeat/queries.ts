"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchHeartbeats, fetchRegistrySettings, saveRegistrySettings } from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

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

/** Umbrales de liveness del proyecto (override o defaults globales). */
export function useProjectRegistrySettings(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.registrySettings(projectId),
    enabled: !!projectId,
    queryFn: () => fetchRegistrySettings(projectId),
  });
}

export function useSaveRegistrySettings(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      intervalSeconds: number;
      timeoutSeconds: number;
      /** Omitir (undefined) preserva la config existente de alerta offline. */
      offlineNotifyEnabled?: boolean;
      offlineNotifyEmail?: string | null;
    }) => withCsrf((token) => saveRegistrySettings(projectId, vars, token)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.projects.registrySettings(projectId) });
      // La liveness mostrada depende de los umbrales → refresca el listado.
      qc.invalidateQueries({ queryKey: queryKeys.projects.heartbeats(projectId) });
    },
  });
}
