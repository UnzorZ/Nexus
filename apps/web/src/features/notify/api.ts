import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type NotificationChannel = "EMAIL";

export type NotificationStatus = "PENDING" | "SENT" | "FAILED";

export type NotificationTemplate = {
  id: string;
  sequence: number;
  name: string;
  channel: NotificationChannel;
  subject: string;
  bodyTemplate: string;
  variables: Record<string, string>;
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

type TemplateBody = {
  name: string;
  subject: string;
  bodyTemplate: string;
  variables: Record<string, string>;
};

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

export type SmtpSettings = {
  projectId: string;
  host: string | null;
  port: number;
  username: string | null;
  from: string | null;
  passwordConfigured: boolean;
  tlsMode: "PUBLIC" | "PINNED";
  trustedCaConfigured: boolean;
  updatedAt: string | null;
};

export type SmtpConnectionCheck = { ok: boolean; message: string };

export async function fetchSmtpSettings(
  projectId: string,
): Promise<SmtpSettings> {
  return apiClient.get<SmtpSettings>(
    apiRoutes.panel.projects.notify.smtp(projectId),
    { redirect: "manual", errorMessage: "No se pudo cargar la configuración SMTP." },
  );
}

export async function saveSmtpSettings(
  projectId: string,
  body: {
    host: string;
    port: number;
    username: string;
    from: string;
    password: string;
    tlsMode: "PUBLIC" | "PINNED";
    trustedCaPem?: string;
  },
  csrfToken: string,
): Promise<SmtpSettings> {
  return apiClient.put<SmtpSettings>(
    apiRoutes.panel.projects.notify.smtp(projectId),
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo guardar la configuración SMTP.",
    },
  );
}

/** Comprueba la conexión SMTP guardada (STARTTLS verificado + AUTH) sin enviar correo. */
export async function testSmtpConnection(
  projectId: string,
  csrfToken: string,
): Promise<SmtpConnectionCheck> {
  return apiClient.post<SmtpConnectionCheck>(
    apiRoutes.panel.projects.notify.smtpTestConnection(projectId),
    {},
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo comprobar la conexión SMTP.",
    },
  );
}

export type RenderedTemplate = { subject: string; body: string };

export async function previewNotifyTemplate(
  projectId: string,
  templateId: string,
  variables: Record<string, string>,
  csrfToken: string,
): Promise<RenderedTemplate> {
  return apiClient.post<RenderedTemplate>(
    apiRoutes.panel.projects.notify.preview(projectId, templateId),
    { variables },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo previsualizar la plantilla.",
    },
  );
}

export type GlobalNotifyVariables = {
  projectId: string;
  variables: Record<string, string>;
  updatedAt: string | null;
};

export async function fetchNotifyVariables(
  projectId: string,
): Promise<GlobalNotifyVariables> {
  return apiClient.get<GlobalNotifyVariables>(
    apiRoutes.panel.projects.notify.variables(projectId),
    { redirect: "manual", errorMessage: "No se pudieron cargar las variables." },
  );
}

export async function saveNotifyVariables(
  projectId: string,
  variables: Record<string, string>,
  csrfToken: string,
): Promise<GlobalNotifyVariables> {
  return apiClient.put<GlobalNotifyVariables>(
    apiRoutes.panel.projects.notify.variables(projectId),
    { variables },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudieron guardar las variables.",
    },
  );
}

export async function sendTestNotification(
  projectId: string,
  body: {
    to: string;
    templateName?: string;
    subject?: string;
    body?: string;
    variables?: Record<string, string>;
  },
  csrfToken: string,
): Promise<Notification> {
  return apiClient.post<Notification>(
    apiRoutes.panel.projects.notify.test(projectId),
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo enviar el email de prueba.",
    },
  );
}

