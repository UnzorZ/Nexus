export const API_BASE_URL =
  process.env.NEXT_PUBLIC_NEXUS_API_BASE_URL ??
  process.env.NEXUS_API_BASE_URL ??
  "http://localhost:8080";

const CONFIGURED_FRONTEND_BASE_URL =
  process.env.NEXUS_FRONTEND_BASE_URL ?? "http://localhost:3000";

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
      login: apiUrl("/panel/login"),
      logout: apiUrl("/api/panel/v1/session/logout"),
      me: apiUrl("/api/panel/v1/me"),
    },
  },
} as const;

export function buildPanelLoginUrl(continuePath = "/dashboard") {
  const frontendBaseUrl =
    typeof window === "undefined"
      ? CONFIGURED_FRONTEND_BASE_URL
      : window.location.origin;
  const continueUrl = new URL(continuePath, frontendBaseUrl).toString();
  const loginUrl = new URL(apiRoutes.panel.session.login);
  loginUrl.searchParams.set("continue", continueUrl);
  return loginUrl.toString();
}
