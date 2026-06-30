"use client";

import { useCallback, useEffect, useState } from "react";
import { NexusApiError } from "@/lib/api/client";
import { fetchHeartbeats, type HeartbeatInstance } from "./api";

export type UseProjectHeartbeatsResult = {
  instances: HeartbeatInstance[] | null;
  loading: boolean;
  error: string | null;
  refresh: () => void;
};

/** Carga el listado de heartbeats de un proyecto (panel, solo lectura). */
export function useProjectHeartbeats(
  projectId: string,
): UseProjectHeartbeatsResult {
  const [instances, setInstances] = useState<HeartbeatInstance[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!projectId) {
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      setError(null);
      setInstances(await fetchHeartbeats(projectId));
    } catch (err) {
      setError(
        err instanceof NexusApiError
          ? err.message
          : "No se pudieron cargar los heartbeats.",
      );
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    // Data fetch on mount: react-hooks/set-state-in-effect is overly strict for
    // fetching (the established pattern across the codebase, e.g. useProjectModules,
    // ProjectProvider). Broader repo lint debt is pre-existing and out of scope here.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  return { instances, loading, error, refresh: load };
}
