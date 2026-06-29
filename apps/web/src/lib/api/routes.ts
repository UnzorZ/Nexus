export const API_BASE_URL =
  process.env.NEXT_PUBLIC_NEXUS_API_BASE_URL ??
  process.env.NEXUS_API_BASE_URL ??
  "http://localhost:8080";

function apiUrl(path: string) {
  return new URL(path, API_BASE_URL).toString();
}

export const apiRoutes = {
  panel: {
    accounts: {
      root: apiUrl("/api/panel/v1/accounts"),
      byId: (accountId: string) =>
        apiUrl(`/api/panel/v1/accounts/${encodeURIComponent(accountId)}`),
    },
    session: {
      csrf: apiUrl("/api/panel/v1/csrf"),
      loginJson: apiUrl("/api/panel/v1/session/login"),
      login: apiUrl("/panel/login"),
      frontendLogin: "/login",
      logout: apiUrl("/api/panel/v1/session/logout"),
      me: apiUrl("/api/panel/v1/me"),
    },
    sessions: {
      root: apiUrl("/api/panel/v1/sessions"),
      byId: (publicSessionId: string) =>
        apiUrl(
          `/api/panel/v1/sessions/${encodeURIComponent(publicSessionId)}`,
        ),
    },
    projects: {
      root: apiUrl("/api/panel/v1/projects"),
      byId: (projectId: string) =>
        apiUrl(`/api/panel/v1/projects/${encodeURIComponent(projectId)}`),
      restore: (projectId: string) =>
        apiUrl(
          `/api/panel/v1/projects/${encodeURIComponent(projectId)}/restore`,
        ),
      modules: {
        root: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/modules`,
          ),
        byKey: (projectId: string, key: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/modules/${encodeURIComponent(key)}`,
          ),
      },
      permissions: {
        root: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/permissions`,
          ),
        byId: (projectId: string, permissionId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/permissions/${encodeURIComponent(permissionId)}`,
          ),
      },
      roles: {
        root: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/roles`,
          ),
        byId: (projectId: string, roleId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/roles/${encodeURIComponent(roleId)}`,
          ),
        permissions: (projectId: string, roleId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/roles/${encodeURIComponent(roleId)}/permissions`,
          ),
      },
    },
  },
} as const;
