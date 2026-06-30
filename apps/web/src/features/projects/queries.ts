"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  archiveProject,
  createProject,
  fetchProject,
  fetchProjects,
  restoreProject,
  updateProject,
  type CreateProjectPayload,
  type UpdateProjectPayload,
} from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

/** Listado de proyectos del panel (picker). */
export function useProjects() {
  return useQuery({
    queryKey: queryKeys.projects.all(),
    queryFn: () => fetchProjects(),
  });
}

/** Ficha de un proyecto (la consume ProjectProvider vía contexto). */
export function useProjectDetail(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.detail(projectId),
    enabled: !!projectId,
    queryFn: () => fetchProject(projectId),
  });
}

export function useCreateProject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateProjectPayload) =>
      withCsrf((token) => createProject(payload, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.all() }),
  });
}

export function useUpdateProject(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateProjectPayload) =>
      withCsrf((token) => updateProject(projectId, payload, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.detail(projectId) }),
  });
}

export function useArchiveProject(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => withCsrf((token) => archiveProject(projectId, token)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.projects.detail(projectId) });
      qc.invalidateQueries({ queryKey: queryKeys.projects.all() });
    },
  });
}

export function useRestoreProject(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => withCsrf((token) => restoreProject(projectId, token)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.projects.detail(projectId) });
      qc.invalidateQueries({ queryKey: queryKeys.projects.all() });
    },
  });
}
