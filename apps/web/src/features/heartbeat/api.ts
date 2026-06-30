import { apiClient } from "@/lib/api/client";
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
  apiKeyId: string;
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
