"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchProjectModules,
  setModuleState,
  type ProjectModuleStatus,
} from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

/** Mensajes de error específicos de módulos para toMessage (compartido por las
 * dos páginas de módulos). */
export const MODULE_MESSAGES = {
  permission: "You don't have permission to change modules for this project.",
  notFound: "This module no longer exists.",
};

/** Estado de los módulos de un proyecto (panel). */
export function useProjectModules(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.modules(projectId),
    enabled: !!projectId,
    queryFn: () => fetchProjectModules(projectId),
  });
}

/**
 * Activa/desactiva un módulo con **update optimista**: parchea la caché al
 * iniciar (feedback instantáneo del switch), hace rollback en error y revalida
 * al terminar. El estado "toggling" se trackea por clave (Set local) para
 * deshabilitar sólo el switch afectado, no toda la lista.
 */
export function useSetModuleEnabled(projectId: string) {
  const qc = useQueryClient();
  const key = queryKeys.projects.modules(projectId);
  const [togglingKeys, setTogglingKeys] = useState<Set<string>>(
    () => new Set(),
  );

  const mutation = useMutation({
    mutationFn: (vars: { key: string; enabled: boolean }) =>
      withCsrf((token) =>
        setModuleState(projectId, vars.key, vars.enabled, token),
      ),
    onMutate: async (vars) => {
      await qc.cancelQueries({ queryKey: key });
      const prev = qc.getQueryData<ProjectModuleStatus[]>(key);
      qc.setQueryData<ProjectModuleStatus[]>(key, (old) =>
        (old ?? []).map((m) =>
          m.key === vars.key ? { ...m, enabled: vars.enabled } : m,
        ),
      );
      return { prev };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(key, ctx.prev);
    },
    onSettled: () => qc.invalidateQueries({ queryKey: key }),
  });

  async function setEnabled(moduleKey: string, enabled: boolean) {
    if (togglingKeys.has(moduleKey)) return;
    setTogglingKeys((prev) => new Set(prev).add(moduleKey));
    try {
      await mutation.mutateAsync({ key: moduleKey, enabled });
    } catch {
      /* rollback vía onError; el error queda en mutation.error */
    } finally {
      setTogglingKeys((prev) => {
        const next = new Set(prev);
        next.delete(moduleKey);
        return next;
      });
    }
  }

  const isToggling = (moduleKey: string) => togglingKeys.has(moduleKey);

  return { setEnabled, isToggling, error: mutation.error };
}
