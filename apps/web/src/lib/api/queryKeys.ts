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
    users: (projectId: string) => ["projects", projectId, "users"] as const,
    userRoles: (projectId: string, userId: string) =>
      ["projects", projectId, "users", userId, "roles"] as const,
    oauthClients: (projectId: string) =>
      ["projects", projectId, "oauth-clients"] as const,
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
    registrySettings: (projectId: string) =>
      ["projects", projectId, "registry", "settings"] as const,
    audit: (projectId: string, rangeMs: number) =>
      ["projects", projectId, "audit", rangeMs] as const,
    config: (projectId: string) =>
      ["projects", projectId, "config"] as const,
    metrics: (projectId: string) =>
      ["projects", projectId, "metrics"] as const,
    notifyTemplates: (projectId: string) =>
      ["projects", projectId, "notify", "templates"] as const,
    notifications: (projectId: string) =>
      ["projects", projectId, "notify", "notifications"] as const,
    notifySmtp: (projectId: string) =>
      ["projects", projectId, "notify", "smtp"] as const,
    notifyVariables: (projectId: string) =>
      ["projects", projectId, "notify", "variables"] as const,
    vaultSecrets: (projectId: string) =>
      ["projects", projectId, "vault", "secrets"] as const,
    vaultEncryption: (projectId: string) =>
      ["projects", projectId, "vault", "encryption"] as const,
  },

  sessions: () => ["sessions"] as const,

  instance: {
    smtp: () => ["instance", "smtp"] as const,
    status: () => ["instance", "status"] as const,
    settings: () => ["instance", "settings"] as const,
  },
} as const;
