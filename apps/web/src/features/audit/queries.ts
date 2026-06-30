"use client";

import { useInfiniteQuery } from "@tanstack/react-query";
import { fetchProjectAudit } from "./api";
import { queryKeys } from "@/lib/api/queryKeys";

const PAGE_SIZE = 50;

/**
 * Log de auditoría de un proyecto (panel, solo lectura), PAGINADO con "cargar
 * más" (useInfiniteQuery). El backend pagina de 50 en 50 (máx. 100) y devuelve
 * {@code last}; aquí se pide la siguiente página mientras no sea la última.
 *
 * `rangeMs` acota la ventana temporal (`since` en el servidor) y se calcula el
 * instante DENTRO del `queryFn` (async) para no llamar a `Date.now()` durante el
 * render ni meterlo en la query key (pureza del React Compiler).
 */
export function useProjectAudit(projectId: string, rangeMs = 0) {
  return useInfiniteQuery({
    queryKey: queryKeys.projects.audit(projectId, rangeMs),
    enabled: !!projectId,
    initialPageParam: 0,
    queryFn: ({ pageParam }) => {
      const since =
        rangeMs > 0 ? new Date(Date.now() - rangeMs).toISOString() : undefined;
      return fetchProjectAudit(projectId, {
        since,
        page: pageParam,
        size: PAGE_SIZE,
      });
    },
    getNextPageParam: (lastPage) =>
      lastPage.last ? undefined : lastPage.page + 1,
  });
}
