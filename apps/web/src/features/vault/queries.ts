"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createSecret,
  deleteSecret,
  fetchSecrets,
  fetchVaultEncryption,
  revealMasterKey,
  revealSecretValue,
  rotateMasterKey,
  rotateSecret,
  type VaultCipher,
} from "./api";
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
    mutationFn: (vars: { key: string; value: string; cipher: VaultCipher }) =>
      withCsrf((token) =>
        createSecret(projectId, vars.key, vars.value, vars.cipher, token),
      ),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.vaultSecrets(projectId),
      }),
  });
}

export function useRotateVaultSecret(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { key: string; value: string; cipher: VaultCipher }) =>
      withCsrf((token) =>
        rotateSecret(projectId, vars.key, vars.value, vars.cipher, token),
      ),
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

/** Revela (descifra) el valor de un secreto desde el panel — bajo demanda. */
export function useRevealVaultSecret(projectId: string) {
  return useMutation({
    mutationFn: (key: string) =>
      withCsrf((token) => revealSecretValue(projectId, key, token)),
  });
}

/** Configuración de cifrado del vault (info, no revela la master key). */
export function useVaultEncryption(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.vaultEncryption(projectId),
    enabled: !!projectId,
    queryFn: () => fetchVaultEncryption(projectId),
  });
}

/** Revela la master key efectiva del proyecto (Manage, auditado). */
export function useRevealVaultMasterKey(projectId: string) {
  return useMutation({
    mutationFn: () => withCsrf((token) => revealMasterKey(projectId, token)),
  });
}

/** Rota la master key: re-cifra todos los secretos del proyecto. */
export function useRotateVaultMasterKey(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (masterKey: string) =>
      withCsrf((token) => rotateMasterKey(projectId, masterKey, token)),
    onSuccess: () => {
      qc.invalidateQueries({
        queryKey: queryKeys.projects.vaultSecrets(projectId),
      });
      qc.invalidateQueries({
        queryKey: queryKeys.projects.vaultEncryption(projectId),
      });
    },
  });
}
