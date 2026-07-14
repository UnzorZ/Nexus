import { apiClient } from "@/lib/api/client";
import { apiRoutes } from "@/lib/api/routes";

export type MetricPoint = {
  value: number;
  tags: Record<string, string>;
  recordedAt: string;
};

export type MetricSeries = {
  name: string;
  tags: Record<string, string>;
  lastValue: number;
  lastRecordedAt: string;
  pointCount: number;
  points: MetricPoint[];
};

/** Series de métricas agregadas por (nombre + tags) — cada tagset es su propia serie. */
export async function fetchMetrics(
  projectId: string,
): Promise<MetricSeries[]> {
  return apiClient.get<MetricSeries[]>(
    apiRoutes.panel.projects.metrics.root(projectId),
    { redirect: "manual", errorMessage: "No se pudieron cargar las métricas." },
  );
}
