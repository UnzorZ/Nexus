import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type ProjectSummary = {
  id: string;
  slug: string;
  name: string;
  status: "ACTIVE" | "SUSPENDED" | "ARCHIVED";
  createdAt: string;
};

export type ProjectDetails = {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  status: "ACTIVE" | "SUSPENDED" | "ARCHIVED";
  publicBaseUrl: string | null;
  createdAt: string;
  updatedAt: string;
  canManage: boolean;
  canDelete: boolean;
};

export type CreateProjectPayload = {
  slug: string;
  name: string;
  description?: string | null;
  publicBaseUrl?: string | null;
};

export type UpdateProjectPayload = {
  name: string;
  description: string | null;
  publicBaseUrl: string | null;
};

export function parseFieldErrors(detail: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const part of detail.split(/\s*;\s*/)) {
    const match = part.match(/^(\w+):\s*(.+)$/);
    if (match) {
      out[match[1]] = match[2];
    }
  }
  return out;
}

export async function fetchProjects(): Promise<ProjectSummary[]> {
  return apiClient.get<ProjectSummary[]>(apiRoutes.panel.projects.root, {
    redirect: "manual",
    errorMessage: "No se pudieron cargar los proyectos.",
  });
}

export async function fetchProject(
  projectId: string,
): Promise<ProjectDetails> {
  return apiClient.get<ProjectDetails>(
    apiRoutes.panel.projects.byId(projectId),
    {
      redirect: "manual",
      errorMessage: "No se pudo cargar el proyecto.",
    },
  );
}

export async function createProject(
  payload: CreateProjectPayload,
  csrfToken: string,
): Promise<ProjectDetails> {
  return apiClient.post<ProjectDetails>(
    apiRoutes.panel.projects.root,
    payload,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo crear el proyecto.",
    },
  );
}

export async function updateProject(
  projectId: string,
  payload: UpdateProjectPayload,
  csrfToken: string,
): Promise<ProjectDetails> {
  return apiClient.patch<ProjectDetails>(
    apiRoutes.panel.projects.byId(projectId),
    payload,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo guardar el proyecto.",
    },
  );
}

export async function archiveProject(
  projectId: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.delete<null>(apiRoutes.panel.projects.byId(projectId), {
    headers: { [CSRF_HEADER_NAME]: csrfToken },
    redirect: "manual",
    errorMessage: "No se pudo archivar el proyecto.",
  });
}

export async function restoreProject(
  projectId: string,
  csrfToken: string,
): Promise<ProjectDetails> {
  return apiClient.post<ProjectDetails>(
    apiRoutes.panel.projects.restore(projectId),
    null,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo restaurar el proyecto.",
    },
  );
}
