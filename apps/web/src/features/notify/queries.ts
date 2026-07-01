"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createNotifyTemplate,
  deleteNotifyTemplate,
  fetchNotifications,
  fetchNotifyTemplates,
  fetchNotifyVariables,
  fetchSmtpSettings,
  previewNotifyTemplate,
  saveNotifyVariables,
  saveSmtpSettings,
  sendTestNotification,
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
      variables: Record<string, string>;
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
      variables: Record<string, string>;
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

/** Configuración SMTP del proyecto (lectura). */
export function useProjectSmtpSettings(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.notifySmtp(projectId),
    enabled: !!projectId,
    queryFn: () => fetchSmtpSettings(projectId),
  });
}

export function useSaveSmtpSettings(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      host: string;
      port: number;
      username: string;
      from: string;
      password: string;
    }) => withCsrf((token) => saveSmtpSettings(projectId, vars, token)),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.notifySmtp(projectId),
      }),
  });
}

/** Previsualiza una plantilla (render sin envío). */
export function usePreviewNotifyTemplate(projectId: string) {
  return useMutation({
    mutationFn: (vars: { templateId: string; variables: Record<string, string> }) =>
      withCsrf((token) =>
        previewNotifyTemplate(projectId, vars.templateId, vars.variables, token),
      ),
  });
}

/** Variables globales del proyecto (aplican a todos los correos). */
export function useProjectNotifyVariables(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.notifyVariables(projectId),
    enabled: !!projectId,
    queryFn: () => fetchNotifyVariables(projectId),
  });
}

export function useSaveNotifyVariables(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (variables: Record<string, string>) =>
      withCsrf((token) => saveNotifyVariables(projectId, variables, token)),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.notifyVariables(projectId),
      }),
  });
}

/** Envía un email de prueba a una dirección del usuario. */
export function useSendTestNotify(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      to: string;
      templateName?: string;
      subject?: string;
      body?: string;
      variables?: Record<string, string>;
    }) => withCsrf((token) => sendTestNotification(projectId, vars, token)),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: queryKeys.projects.notifications(projectId),
      }),
  });
}
