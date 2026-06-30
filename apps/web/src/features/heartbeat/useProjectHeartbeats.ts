"use client";

import { useCallback, useEffect, useState } from "react";
import { NexusApiError } from "@/lib/api/client";
import { fetchHeartbeats, type HeartbeatInstance } from "./api";

/** Refresco en vivo del panel (es de solo lectura, así que es seguro). */
const POLL_INTERVAL_MS = 10_000;

export type UseProjectHeartbeatsResult = {
  instances: HeartbeatInstance[] | null;
  loading: boolean;
  error: string | null;
  refresh: () => void;
};

/**
 * Carga el listado de heartbeats de un proyecto (panel, solo lectura) y lo
 * refresca por polling cada {@link POLL_INTERVAL_MS}. El polling es silencioso:
 * no toggles `loading` ni muestra error ante un fallo transitorio (conserva los
 * últimos datos); el `refresh` manual sí hace una carga completa (botón Retry).
 *
 * `load` depende solo de `projectId` (estable) para que ni el effect de montaje
 * ni el del polling se redisparen al cambiar el estado con cada latido.
 */
export function useProjectHeartbeats(
  projectId: string,
): UseProjectHeartbeatsResult {
  const [instances, setInstances] = useState<HeartbeatInstance[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (opts: { silent?: boolean } = {}) => {
      if (!projectId) {
        setLoading(false);
        return;
      }
      const silent = opts.silent ?? false;
      try {
        if (!silent) {
          setLoading(true);
        }
        setInstances(await fetchHeartbeats(projectId));
        setError(null);
      } catch (err) {
        // Solo mostramos error en cargas explícitas; un fallo de polling
        // transitorio se ignora y se conservan los últimos datos.
        if (!silent) {
          setError(
            err instanceof NexusApiError
              ? err.message
              : "No se pudieron cargar los heartbeats.",
          );
        }
      } finally {
        if (!silent) {
          setLoading(false);
        }
      }
    },
    [projectId],
  );

  useEffect(() => {
    // Data fetch on mount: react-hooks/set-state-in-effect is overly strict for
    // fetching (the established pattern across the codebase, e.g. useProjectModules,
    // ProjectProvider). Broader repo lint debt is pre-existing and out of scope here.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  useEffect(() => {
    if (!projectId) return;
    const id = window.setInterval(() => load({ silent: true }), POLL_INTERVAL_MS);
    return () => window.clearInterval(id);
  }, [load, projectId]);

  return { instances, loading, error, refresh: () => load() };
}
