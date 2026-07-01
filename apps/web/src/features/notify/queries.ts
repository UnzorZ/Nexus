"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createNotifyTemplate,
  deleteNotifyTemplate,
  fetchNotifications,
  fetchNotifyTemplates,
  updateNotifyTemplate,
} from "./api";
import { queryKeys } from "@/lib/api/queryKeys";
import { withCsrf } from "@/lib/api/csrf";

export function useProjectNotifyTemplates(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.notifyTemplates(projectId),
    enabled: !!projectId,
    queryFn: () => fetchNotifyTemplates(projectId),
  });
}

export function useCreateNotifyTemplate(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      name: string;
      subject: string;
      bodyTemplate: string;
    }) => withCsrf((token) => createNotifyTemplate(projectId, vars, token)),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.notifyTemplates(projectId),
      }),
  });
}

export function useUpdateNotifyTemplate(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      templateId: string;
      name: string;
      subject: string;
      bodyTemplate: string;
    }) =>
      withCsrf((token) =>
        updateNotifyTemplate(projectId, vars.templateId, vars, token),
      ),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.notifyTemplates(projectId),
      }),
  });
}

export function useDeleteNotifyTemplate(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (templateId: string) =>
      withCsrf((token) => deleteNotifyTemplate(projectId, templateId, token)),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.notifyTemplates(projectId),
      }),
  });
}

export function useProjectNotifications(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.notifications(projectId),
    enabled: !!projectId,
    queryFn: () => fetchNotifications(projectId),
  });
}
