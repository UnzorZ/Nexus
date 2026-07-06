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
    instance: {
      smtp: apiUrl("/api/panel/v1/instance/smtp"),
      smtpTestConnection: apiUrl("/api/panel/v1/instance/smtp/test-connection"),
      status: apiUrl("/api/panel/v1/instance/status"),
      settings: apiUrl("/api/panel/v1/instance/settings"),
      registration: apiUrl("/api/panel/v1/instance/registration"),
      modulesDefaults: apiUrl("/api/panel/v1/instance/modules-defaults"),
      heartbeat: apiUrl("/api/panel/v1/instance/heartbeat"),
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
      apiKeys: {
        root: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/api-keys`,
          ),
        byId: (projectId: string, keyId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/api-keys/${encodeURIComponent(keyId)}`,
          ),
        rotate: (projectId: string, keyId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/api-keys/${encodeURIComponent(keyId)}/rotate`,
          ),
      },
      members: {
        root: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/members`,
          ),
        byId: (projectId: string, memberId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/members/${encodeURIComponent(memberId)}`,
          ),
        transferOwnership: (projectId: string, memberId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/members/${encodeURIComponent(memberId)}/transfer-ownership`,
          ),
      },
      users: {
        root: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/users`,
          ),
        byId: (projectId: string, userId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/users/${encodeURIComponent(userId)}`,
          ),
        statusAction: (
          projectId: string,
          userId: string,
          action: "suspend" | "reactivate" | "disable",
        ) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/users/${encodeURIComponent(userId)}/${action}`,
          ),
        resetPassword: (projectId: string, userId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/users/${encodeURIComponent(userId)}/reset-password`,
          ),
      },
      oauthClients: {
        root: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/oauth-clients`,
          ),
        byId: (projectId: string, id: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/oauth-clients/${encodeURIComponent(id)}`,
          ),
        rotate: (projectId: string, id: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/oauth-clients/${encodeURIComponent(id)}/rotate`,
          ),
        disable: (projectId: string, id: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/oauth-clients/${encodeURIComponent(id)}/disable`,
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
      heartbeats: {
        root: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/heartbeats`,
          ),
        settings: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/heartbeats/settings`,
          ),
      },
      audit: {
        root: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/audit`,
          ),
      },
      config: {
        root: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/config`,
          ),
        byKey: (projectId: string, key: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/config/${encodeURIComponent(key)}`,
          ),
      },
      metrics: {
        root: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/metrics`,
          ),
      },
      notify: {
        templatesRoot: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/notify/templates`,
          ),
        templateById: (projectId: string, templateId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/notify/templates/${encodeURIComponent(templateId)}`,
          ),
        preview: (projectId: string, templateId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/notify/templates/${encodeURIComponent(templateId)}/preview`,
          ),
        smtp: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/notify/smtp`,
          ),
        smtpTestConnection: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/notify/smtp/test-connection`,
          ),
        variables: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/notify/variables`,
          ),
        test: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/notify/test`,
          ),
        notificationsRoot: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/notify/notifications`,
          ),
      },
      vault: {
        secretsRoot: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/vault/secrets`,
          ),
        secretByKey: (projectId: string, key: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/vault/secrets/${encodeURIComponent(key)}`,
          ),
        secretValue: (projectId: string, key: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/vault/secrets/${encodeURIComponent(key)}/value`,
          ),
        encryption: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/vault/encryption`,
          ),
        masterKey: (projectId: string) =>
          apiUrl(
            `/api/panel/v1/projects/${encodeURIComponent(projectId)}/vault/master-key`,
          ),
      },
    },
  },
} as const;
