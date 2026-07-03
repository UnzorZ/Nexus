"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createOauthClient,
  deleteOauthClient,
  disableOauthClient,
  fetchOauthClients,
  rotateOauthClient,
  updateOauthClient,
  type CreateOauthClientPayload,
  type UpdateOauthClientPayload,
} from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

/** Clientes OAuth del proyecto (panel). */
export function useProjectOauthClients(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.oauthClients(projectId),
    enabled: !!projectId,
    queryFn: () => fetchOauthClients(projectId),
  });
}

export function useCreateOauthClient(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateOauthClientPayload) =>
      withCsrf((token) => createOauthClient(projectId, payload, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.oauthClients(projectId) }),
  });
}

export function useUpdateOauthClient(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; payload: UpdateOauthClientPayload }) =>
      withCsrf((token) => updateOauthClient(projectId, vars.id, vars.payload, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.oauthClients(projectId) }),
  });
}

/** Rota el secreto (sólo clientes confidenciales); devuelve el nuevo secreto una vez. */
export function useRotateOauthClient(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      withCsrf((token) => rotateOauthClient(projectId, id, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.oauthClients(projectId) }),
  });
}

export function useDisableOauthClient(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      withCsrf((token) => disableOauthClient(projectId, id, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.oauthClients(projectId) }),
  });
}

export function useDeleteOauthClient(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      withCsrf((token) => deleteOauthClient(projectId, id, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.oauthClients(projectId) }),
  });
}
