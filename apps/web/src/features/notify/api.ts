import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type NotificationChannel = "EMAIL";

export type NotificationStatus = "PENDING" | "SENT" | "FAILED";

export type NotificationTemplate = {
  id: string;
  name: string;
  channel: NotificationChannel;
  subject: string;
  bodyTemplate: string;
  createdAt: string;
  updatedAt: string;
};

export type Notification = {
  id: string;
  channel: NotificationChannel;
  recipient: string;
  subject: string;
  status: NotificationStatus;
  error: string | null;
  sentAt: string | null;
  createdAt: string;
};

type TemplateBody = { name: string; subject: string; bodyTemplate: string };

export async function fetchNotifyTemplates(
  projectId: string,
): Promise<NotificationTemplate[]> {
  return apiClient.get<NotificationTemplate[]>(
    apiRoutes.panel.projects.notify.templatesRoot(projectId),
    { redirect: "manual", errorMessage: "No se pudieron cargar las plantillas." },
  );
}

export async function createNotifyTemplate(
  projectId: string,
  body: TemplateBody,
  csrfToken: string,
): Promise<NotificationTemplate> {
  return apiClient.post<NotificationTemplate>(
    apiRoutes.panel.projects.notify.templatesRoot(projectId),
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo crear la plantilla.",
    },
  );
}

export async function updateNotifyTemplate(
  projectId: string,
  templateId: string,
  body: TemplateBody,
  csrfToken: string,
): Promise<NotificationTemplate> {
  return apiClient.patch<NotificationTemplate>(
    apiRoutes.panel.projects.notify.templateById(projectId, templateId),
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo actualizar la plantilla.",
    },
  );
}

export async function deleteNotifyTemplate(
  projectId: string,
  templateId: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.delete<void>(
    apiRoutes.panel.projects.notify.templateById(projectId, templateId),
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo eliminar la plantilla.",
    },
  );
}

export async function fetchNotifications(
  projectId: string,
): Promise<Notification[]> {
  return apiClient.get<Notification[]>(
    apiRoutes.panel.projects.notify.notificationsRoot(projectId),
    {
      redirect: "manual",
      errorMessage: "No se pudo cargar el historial de envíos.",
    },
  );
}
