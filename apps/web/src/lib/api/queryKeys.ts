/**
 * Claves de caché jerárquicas para TanStack Query.
 *
 * Anidar todo bajo `["projects", projectId, ...]` permite invalidar de golpe
 * el subárbol completo de un proyecto, p. ej. tras archivar/restaurar:
 *
 *     queryClient.invalidateQueries({ queryKey: queryKeys.projects.detail(id) })
 *
 * invalida la ficha del proyecto Y todos sus recursos (members, roles, ...).
 * Todas las claves son funciones que devuelven tuplas `as const` para que el
 * tipo se infiera como readonly y estable.
 */
export const queryKeys = {
  me: () => ["me"] as const,

  projects: {
    all: () => ["projects"] as const,
    detail: (projectId: string) => ["projects", projectId] as const,
    members: (projectId: string) => ["projects", projectId, "members"] as const,
    roles: (projectId: string) => ["projects", projectId, "roles"] as const,
    permissions: (projectId: string) =>
      ["projects", projectId, "permissions"] as const,
    modules: (projectId: string) => ["projects", projectId, "modules"] as const,
    module: (projectId: string, moduleKey: string) =>
      ["projects", projectId, "modules", moduleKey] as const,
    apiKeys: (projectId: string) =>
      ["projects", projectId, "api-keys"] as const,
    heartbeats: (projectId: string) =>
      ["projects", projectId, "heartbeats"] as const,
    audit: (projectId: string, rangeMs: number) =>
      ["projects", projectId, "audit", rangeMs] as const,
    config: (projectId: string) =>
      ["projects", projectId, "config"] as const,
    metrics: (projectId: string) =>
      ["projects", projectId, "metrics"] as const,
  },

  sessions: () => ["sessions"] as const,
} as const;
