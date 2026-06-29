import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type ProjectModuleStatus = {
  key: string;
  enabled: boolean;
  enabledByDefault: boolean;
};

export async function fetchProjectModules(
  projectId: string,
): Promise<ProjectModuleStatus[]> {
  return apiClient.get<ProjectModuleStatus[]>(
    apiRoutes.panel.projects.modules.root(projectId),
    {
      redirect: "manual",
      errorMessage: "No se pudieron cargar los módulos.",
    },
  );
}

export async function setModuleState(
  projectId: string,
  key: string,
  enabled: boolean,
  csrfToken: string,
): Promise<ProjectModuleStatus> {
  return apiClient.patch<ProjectModuleStatus>(
    apiRoutes.panel.projects.modules.byKey(projectId, key),
    { enabled },
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo actualizar el módulo.",
    },
  );
}
