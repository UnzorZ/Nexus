"use client";

import { createContext, useEffect, use } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { type ProjectDetails } from "@/features/projects/api";
import { useProjectDetail } from "@/features/projects/queries";
import { queryKeys } from "@/lib/api/queryKeys";
import { NexusApiError } from "@/lib/api/client";

export type ProjectContextValue = {
  project: ProjectDetails | null;
  loading: boolean;
  error: string | null;
  /** Invalida la caché del proyecto (y su subárbol) y dispara un refetch.
   * `options.silent` se mantiene por compatibilidad pero se ignora: el refetch
   * en TanStack ya es silencioso (no toggla `loading`). */
  refresh: (options?: { silent?: boolean }) => void;
};

export const ProjectContext = createContext<ProjectContextValue>({
  project: null,
  loading: true,
  error: null,
  refresh: () => {},
});

/**
 * Provee la ficha del proyecto vía contexto (todas las subpáginas la consumen
 * con `useProject()` para el id + breadcrumb). Ahora respaldada por TanStack
 * Query: la caché es global (clave `["projects", id]`), así una invalidación
 * desde settings/transfer actualiza el header y el breadcrumb en todas partes.
 */
export function ProjectProvider({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ projectId: string }>;
}) {
  const { projectId } = use(params);
  const router = useRouter();
  const queryClient = useQueryClient();

  const query = useProjectDetail(projectId);

  // 404 → el proyecto ya no existe (o no es accesible): vuelve al listado.
  useEffect(() => {
    if (query.error instanceof NexusApiError && query.error.status === 404) {
      router.replace("/projects");
    }
  }, [query.error, router]);

  const project = query.data ?? null;
  const loading = query.isLoading;
  const error = query.error
    ? query.error instanceof NexusApiError
      ? query.error.message
      : "No se pudo cargar el proyecto."
    : null;

  const refresh = () => {
    void queryClient.invalidateQueries({
      queryKey: queryKeys.projects.detail(projectId),
    });
  };

  return (
    <ProjectContext.Provider value={{ project, loading, error, refresh }}>
      {children}
    </ProjectContext.Provider>
  );
}
