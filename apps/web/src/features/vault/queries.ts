"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createSecret, deleteSecret, fetchSecrets, rotateSecret } from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

/** Secretos del vault (sólo metadatos; el valor nunca se expone). */
export function useProjectVaultSecrets(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.vaultSecrets(projectId),
    enabled: !!projectId,
    queryFn: () => fetchSecrets(projectId),
  });
}

export function useCreateVaultSecret(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { key: string; value: string }) =>
      withCsrf((token) => createSecret(projectId, vars.key, vars.value, token)),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.vaultSecrets(projectId),
      }),
  });
}

export function useRotateVaultSecret(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { key: string; value: string }) =>
      withCsrf((token) => rotateSecret(projectId, vars.key, vars.value, token)),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.vaultSecrets(projectId),
      }),
  });
}

export function useDeleteVaultSecret(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (key: string) =>
      withCsrf((token) => deleteSecret(projectId, key, token)),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.vaultSecrets(projectId),
      }),
  });
}
