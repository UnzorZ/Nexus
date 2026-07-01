"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createDocumentTemplate,
  deleteDocumentTemplate,
  fetchDocumentRenders,
  fetchDocumentTemplates,
  renderDocumentTemplate,
  updateDocumentTemplate,
} from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

/** Plantillas de documento de un proyecto (panel). */
export function useProjectDocumentTemplates(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.documents(projectId),
    enabled: !!projectId,
    queryFn: () => fetchDocumentTemplates(projectId),
  });
}

export function useCreateDocumentTemplate(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      name: string;
      contentType: string;
      templateBody: string;
    }) => withCsrf((token) => createDocumentTemplate(projectId, vars, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.documents(projectId) }),
  });
}

export function useUpdateDocumentTemplate(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      templateId: string;
      name: string;
      contentType: string;
      templateBody: string;
    }) =>
      withCsrf((token) =>
        updateDocumentTemplate(projectId, vars.templateId, vars, token),
      ),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.documents(projectId) }),
  });
}

export function useDeleteDocumentTemplate(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (templateId: string) =>
      withCsrf((token) => deleteDocumentTemplate(projectId, templateId, token)),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.projects.documents(projectId) }),
  });
}

/** Historial de renders de un proyecto (panel, sólo lectura). */
export function useProjectDocumentRenders(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.documentRenders(projectId),
    enabled: !!projectId,
    queryFn: () => fetchDocumentRenders(projectId),
  });
}

/** Renderiza una plantilla desde el panel; invalida el historial de renders. */
export function useRenderDocumentTemplate(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { templateId: string; variables: Record<string, string> }) =>
      withCsrf((token) =>
        renderDocumentTemplate(projectId, vars.templateId, vars.variables, token),
      ),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.documentRenders(projectId),
      }),
  });
}
