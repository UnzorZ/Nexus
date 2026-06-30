"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createApiKey,
  deleteApiKey,
  fetchApiKeys,
  rotateApiKey,
  updateApiKey,
  type ApiKeyCreated,
  type ApiKeyStatus,
} from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

/** Listado de API keys de un proyecto (resúmenes sin secreto). */
export function useProjectApiKeys(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.apiKeys(projectId),
    enabled: !!projectId,
    queryFn: () => fetchApiKeys(projectId),
  });
}

/** Crea una API key. Devuelve el objeto completo (con el secreto de un solo uso). */
export function useCreateApiKey(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      name: string;
      scopes: string[];
      expiresAt: string | null;
    }) => withCsrf((token) => createApiKey(projectId, vars, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.apiKeys(projectId) }),
  });
}

/** Actualiza nombre/estado/expiración de una API key. */
export function useUpdateApiKey(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      keyId: string;
      name: string;
      status: ApiKeyStatus;
      expiresAt: string | null;
    }) =>
      withCsrf((token) =>
        updateApiKey(projectId, vars.keyId, {
          name: vars.name,
          status: vars.status,
          expiresAt: vars.expiresAt,
        }, token),
      ),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.apiKeys(projectId) }),
  });
}

/** Rota una API key. Devuelve el objeto completo nuevo (con el secreto de un solo uso). */
export function useRotateApiKey(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (keyId: string) =>
      withCsrf((token) => rotateApiKey(projectId, keyId, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.apiKeys(projectId) }),
  });
}

/** Elimina (revoca) una API key. */
export function useDeleteApiKey(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (keyId: string) =>
      withCsrf((token) => deleteApiKey(projectId, keyId, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.apiKeys(projectId) }),
  });
}

/** Tipo devuelto por create/rotate (metadatos + secreto), para que la página lo revele una vez. */
export type { ApiKeyCreated };
