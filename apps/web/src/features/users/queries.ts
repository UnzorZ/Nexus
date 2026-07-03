"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createProjectUser,
  deleteProjectUser,
  disableProjectUser,
  fetchProjectUsers,
  reactivateProjectUser,
  resetProjectUserPassword,
  suspendProjectUser,
  updateProjectUser,
  type CreateProjectUserPayload,
  type UpdateProjectUserPayload,
} from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

/** Listado de usuarios finales de un proyecto (panel). */
export function useProjectUsers(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.users(projectId),
    enabled: !!projectId,
    queryFn: () => fetchProjectUsers(projectId),
  });
}

/** Crea un usuario final (contraseña fijada por el admin; nace ACTIVE). */
export function useCreateProjectUser(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateProjectUserPayload) =>
      withCsrf((token) => createProjectUser(projectId, payload, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.users(projectId) }),
  });
}

/** Actualiza el perfil (displayName + username). */
export function useUpdateProjectUser(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      userId: string;
      payload: UpdateProjectUserPayload;
    }) => withCsrf((token) => updateProjectUser(projectId, vars.userId, vars.payload, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.users(projectId) }),
  });
}

export function useSuspendProjectUser(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) =>
      withCsrf((token) => suspendProjectUser(projectId, userId, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.users(projectId) }),
  });
}

export function useReactivateProjectUser(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) =>
      withCsrf((token) => reactivateProjectUser(projectId, userId, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.users(projectId) }),
  });
}

export function useDisableProjectUser(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) =>
      withCsrf((token) => disableProjectUser(projectId, userId, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.users(projectId) }),
  });
}

export function useDeleteProjectUser(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) =>
      withCsrf((token) => deleteProjectUser(projectId, userId, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.users(projectId) }),
  });
}

/** Reset administrativo de contraseña (nueva contraseña en claro, hashea el backend). */
export function useResetProjectUserPassword(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { userId: string; newPassword: string }) =>
      withCsrf((token) =>
        resetProjectUserPassword(projectId, vars.userId, vars.newPassword, token),
      ),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.users(projectId) }),
  });
}
