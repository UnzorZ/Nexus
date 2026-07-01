import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

export type ConfigValueType = "STRING" | "NUMBER" | "BOOLEAN" | "JSON";

export type ConfigValue = {
  id: string;
  key: string;
  value: string;
  valueType: ConfigValueType;
  createdAt: string;
  updatedAt: string;
};

export async function fetchConfigValues(
  projectId: string,
): Promise<ConfigValue[]> {
  return apiClient.get<ConfigValue[]>(
    apiRoutes.panel.projects.config.root(projectId),
    { redirect: "manual", errorMessage: "No se pudo cargar la configuración." },
  );
}

export async function setConfigValue(
  projectId: string,
  key: string,
  body: { value: string; valueType: ConfigValueType },
  csrfToken: string,
): Promise<ConfigValue> {
  return apiClient.put<ConfigValue>(
    apiRoutes.panel.projects.config.byKey(projectId, key),
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo guardar el valor de configuración.",
    },
  );
}

export async function deleteConfigValue(
  projectId: string,
  key: string,
  csrfToken: string,
): Promise<void> {
  await apiClient.delete<void>(
    apiRoutes.panel.projects.config.byKey(projectId, key),
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudo eliminar el valor de configuración.",
    },
  );
}
