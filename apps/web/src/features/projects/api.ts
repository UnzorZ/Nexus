import { apiClient } from "@/lib/api/client";
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
};

export type CreateProjectPayload = {
  slug: string;
  name: string;
  description?: string | null;
  publicBaseUrl?: string | null;
};

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
      headers: { "X-XSRF-TOKEN": csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo crear el proyecto.",
    },
  );
}
