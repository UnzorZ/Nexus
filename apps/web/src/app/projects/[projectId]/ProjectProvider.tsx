"use client";

import {
  createContext,
  use,
  useCallback,
  useEffect,
  useState,
} from "react";
import { useRouter } from "next/navigation";
import { fetchProject, type ProjectDetails } from "@/features/projects/api";
import { NexusApiError } from "@/lib/api/client";

export type ProjectContextValue = {
  project: ProjectDetails | null;
  loading: boolean;
  error: string | null;
  refresh: (options?: { silent?: boolean }) => void;
};

export const ProjectContext = createContext<ProjectContextValue>({
  project: null,
  loading: true,
  error: null,
  refresh: () => {},
});

export function ProjectProvider({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ projectId: string }>;
}) {
  const { projectId } = use(params);
  const router = useRouter();

  const [project, setProject] = useState<ProjectDetails | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (options?: { silent?: boolean }) => {
    try {
      if (!options?.silent) {
        setLoading(true);
      }
      setError(null);
      const data = await fetchProject(projectId);
      setProject(data);
    } catch (err) {
      if (err instanceof NexusApiError && err.status === 404) {
        router.replace("/projects");
        return;
      }
      const message =
        err instanceof NexusApiError
          ? err.message
          : "No se pudo cargar el proyecto.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [projectId, router]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <ProjectContext.Provider
      value={{ project, loading, error, refresh: load }}
    >
      {children}
    </ProjectContext.Provider>
  );
}
