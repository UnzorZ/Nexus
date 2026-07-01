"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchMetrics } from "./api";
import { queryKeys } from "@/lib/api/queryKeys";

/** Series de métricas de un proyecto (panel, sólo lectura). */
export function useProjectMetrics(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.metrics(projectId),
    enabled: !!projectId,
    queryFn: () => fetchMetrics(projectId),
  });
}
