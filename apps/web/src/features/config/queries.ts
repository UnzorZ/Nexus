"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { deleteConfigValue, fetchConfigValues, setConfigValue } from "./api";
import type { ConfigValueType } from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

/** Valores de configuración de un proyecto (panel). */
export function useProjectConfig(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.config(projectId),
    enabled: !!projectId,
    queryFn: () => fetchConfigValues(projectId),
  });
}

/** Upsert (crear o actualizar) de un valor por clave. */
export function useSetConfigValue(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      key: string;
      value: string;
      valueType: ConfigValueType;
    }) => withCsrf((token) => setConfigValue(projectId, vars.key, vars, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.config(projectId) }),
  });
}

/** Elimina un valor por clave. */
export function useDeleteConfigValue(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (key: string) =>
      withCsrf((token) => deleteConfigValue(projectId, key, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.config(projectId) }),
  });
}
