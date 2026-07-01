import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type DocumentTemplate = {
  id: string;
  name: string;
  contentType: string;
  templateBody: string;
  createdAt: string;
  updatedAt: string;
};

export type DocumentRender = {
  id: string;
  templateName: string;
  variables: Record<string, string>;
  output: string;
  createdAt: string;
};

type TemplateBody = {
  name: string;
  contentType: string;
  templateBody: string;
};

export async function fetchDocumentTemplates(
  projectId: string,
): Promise<DocumentTemplate[]> {
  return apiClient.get<DocumentTemplate[]>(
    apiRoutes.panel.projects.documents.templatesRoot(projectId),
    { redirect: "manual", errorMessage: "No se pudieron cargar las plantillas." },
  );
}

export async function createDocumentTemplate(
  projectId: string,
  body: TemplateBody,
  csrfToken: string,
): Promise<DocumentTemplate> {
  return apiClient.post<DocumentTemplate>(
    apiRoutes.panel.projects.documents.templatesRoot(projectId),
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo crear la plantilla.",
    },
  );
}

export async function updateDocumentTemplate(
  projectId: string,
  templateId: string,
  body: TemplateBody,
  csrfToken: string,
): Promise<DocumentTemplate> {
  return apiClient.patch<DocumentTemplate>(
    apiRoutes.panel.projects.documents.templateById(projectId, templateId),
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo actualizar la plantilla.",
    },
  );
}

export async function deleteDocumentTemplate(
  projectId: string,
  templateId: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.delete<void>(
    apiRoutes.panel.projects.documents.templateById(projectId, templateId),
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo eliminar la plantilla.",
    },
  );
}

export async function fetchDocumentRenders(
  projectId: string,
): Promise<DocumentRender[]> {
  return apiClient.get<DocumentRender[]>(
    apiRoutes.panel.projects.documents.rendersRoot(projectId),
    {
      redirect: "manual",
      errorMessage: "No se pudo cargar el historial de renders.",
    },
  );
}

export type DocumentRenderResult = {
  renderId: string;
  output: string;
  contentType: string;
  renderedAt: string;
};

/** Renderiza una plantilla desde el panel con variables {$var} (no envía nada). */
export async function renderDocumentTemplate(
  projectId: string,
  templateId: string,
  variables: Record<string, string>,
  csrfToken: string,
): Promise<DocumentRenderResult> {
  return apiClient.post<DocumentRenderResult>(
    apiRoutes.panel.projects.documents.render(projectId, templateId),
    { variables },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo renderizar la plantilla.",
    },
  );
}
