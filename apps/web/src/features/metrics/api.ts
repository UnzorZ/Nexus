import { apiClient } from "@/lib/api/client";
import { apiRoutes } from "@/lib/api/routes";

export type MetricPoint = {
  value: number;
  tags: Record<string, string>;
  recordedAt: string;
};

export type MetricSeries = {
  name: string;
  lastValue: number;
  lastRecordedAt: string;
  pointCount: number;
  points: MetricPoint[];
};

/** Series de métricas agregadas por nombre (panel). */
export async function fetchMetrics(
  projectId: string,
): Promise<MetricSeries[]> {
  return apiClient.get<MetricSeries[]>(
    apiRoutes.panel.projects.metrics.root(projectId),
    { redirect: "manual", errorMessage: "No se pudieron cargar las métricas." },
  );
}
