import { apiClient } from "@/lib/api/client";
import { CSRF_HEADER_NAME } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";

/** Liveness derivada (server-side) de una instancia a partir de last_seen. */
export type HeartbeatLiveness = "ONLINE" | "STALE" | "OFFLINE";

/** Vista de una instancia reportando latidos (spec §13.1, listado del panel). */
export type HeartbeatInstance = {
  id: string;
  instanceId: string;
  appName: string;
  appVersion: string | null;
  /** Estado auto-reportado por la instancia (por defecto "up"). */
  status: string;
  liveness: HeartbeatLiveness;
  lastSeenAt: string;
  /** Non-secret key prefix (nxs_<slug>_<partial>) that reported this instance. */
  apiKeyPrefix: string;
  createdAt: string;
};

/** Listado de instancias de un proyecto (panel, solo lectura). */
export async function fetchHeartbeats(
  projectId: string,
): Promise<HeartbeatInstance[]> {
  return apiClient.get<HeartbeatInstance[]>(
    apiRoutes.panel.projects.heartbeats.root(projectId),
    { redirect: "manual", errorMessage: "No se pudieron cargar los heartbeats." },
  );
}

/** Umbrales de liveness de un proyecto (override o defaults globales). */
export type RegistrySettings = {
  projectId: string;
  intervalSeconds: number;
  timeoutSeconds: number;
  offlineNotifyEnabled: boolean;
  offlineNotifyEmail: string | null;
  overridden: boolean;
  updatedAt: string | null;
};

export async function fetchRegistrySettings(
  projectId: string,
): Promise<RegistrySettings> {
  return apiClient.get<RegistrySettings>(
    apiRoutes.panel.projects.heartbeats.settings(projectId),
    { redirect: "manual", errorMessage: "No se pudieron cargar los umbrales." },
  );
}

export async function saveRegistrySettings(
  projectId: string,
  body: {
    intervalSeconds: number;
    timeoutSeconds: number;
    /** Omitir (undefined) preserva la config existente de alerta offline. */
    offlineNotifyEnabled?: boolean;
    offlineNotifyEmail?: string | null;
  },
  csrfToken: string,
): Promise<RegistrySettings> {
  return apiClient.put<RegistrySettings>(
    apiRoutes.panel.projects.heartbeats.settings(projectId),
    body,
    {
      headers: { [CSRF_HEADER_NAME]: csrfToken },
      redirect: "manual",
      errorMessage: "No se pudieron guardar los umbrales.",
    },
  );
}
