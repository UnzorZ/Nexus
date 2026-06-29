"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  fetchProjectModules,
  setModuleState,
  type ProjectModuleStatus,
} from "@/features/modules/api";
import { NexusApiError } from "@/lib/api/client";
import { ensureCsrfToken } from "@/lib/api/csrf";

export type UseProjectModulesResult = {
  modules: ProjectModuleStatus[] | null;
  loading: boolean;
  error: string | null;
  toggleError: string | null;
  isToggling: (key: string) => boolean;
  setEnabled: (key: string, enabled: boolean) => Promise<void>;
  refresh: () => void;
};

export function useProjectModules(projectId: string): UseProjectModulesResult {
  const [modules, setModules] = useState<ProjectModuleStatus[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toggleError, setToggleError] = useState<string | null>(null);
  const [togglingKeys, setTogglingKeys] = useState<Set<string>>(() => new Set());

  const modulesRef = useRef<ProjectModuleStatus[] | null>(null);
  modulesRef.current = modules;

  const togglingRef = useRef<Set<string>>(new Set());

  const load = useCallback(async () => {
    if (!projectId) {
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      setError(null);
      const data = await fetchProjectModules(projectId);
      setModules(data);
    } catch (err) {
      const message =
        err instanceof NexusApiError
          ? err.message
          : "No se pudieron cargar los módulos.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    load();
  }, [load]);

  const isToggling = useCallback(
    (key: string) => togglingKeys.has(key),
    [togglingKeys],
  );

  const setEnabled = useCallback(
    async (key: string, enabled: boolean) => {
      if (!projectId || togglingRef.current.has(key) || !modulesRef.current) {
        return;
      }

      // Capture only this key's prior value so a rollback never clobbers a
      // concurrent toggle of a different module.
      const priorEnabled = modulesRef.current.find((m) => m.key === key)?.enabled;

      togglingRef.current.add(key);
      setTogglingKeys(new Set(togglingRef.current));
      setToggleError(null);

      // Optimistic update — functional so it composes with concurrent toggles.
      setModules(
        (latest) =>
          (latest ?? []).map((m) => (m.key === key ? { ...m, enabled } : m)),
      );

      try {
        const token = await ensureCsrfToken();
        const updated = await setModuleState(projectId, key, enabled, token);
        setModules(
          (latest) =>
            (latest ?? []).map((m) => (m.key === key ? updated : m)),
        );
      } catch (err) {
        // Roll back ONLY this key to its prior value.
        if (priorEnabled !== undefined) {
          setModules(
            (latest) =>
              (latest ?? []).map((m) =>
                m.key === key ? { ...m, enabled: priorEnabled } : m,
              ),
          );
        }
        if (err instanceof NexusApiError && err.status === 403 && err.code === "permission_denied") {
          setToggleError("You don't have permission to change modules for this project.");
        } else if (err instanceof NexusApiError && err.status === 403) {
          setToggleError("Your session expired. Reload the page and try again.");
        } else if (err instanceof NexusApiError && err.status === 404) {
          setToggleError("This module no longer exists.");
        } else {
          setToggleError(
            err instanceof NexusApiError ? err.message : "Something went wrong.",
          );
        }
      } finally {
        togglingRef.current.delete(key);
        setTogglingKeys(new Set(togglingRef.current));
      }
    },
    [projectId],
  );

  return {
    modules,
    loading,
    error,
    toggleError,
    isToggling,
    setEnabled,
    refresh: load,
  };
}
