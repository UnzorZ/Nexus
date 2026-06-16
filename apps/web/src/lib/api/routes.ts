export const API_BASE_URL =
  process.env.NEXT_PUBLIC_NEXUS_API_BASE_URL ??
  process.env.NEXUS_API_BASE_URL ??
  "http://localhost:8080";

const CONFIGURED_FRONTEND_BASE_URL =
  process.env.NEXUS_FRONTEND_BASE_URL ?? "http://localhost:3000";

const CONFIGURED_FRONTEND_ORIGIN = new URL(CONFIGURED_FRONTEND_BASE_URL).origin;

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
    sessions: {
      root: apiUrl("/api/panel/v1/sessions"),
      byId: (publicSessionId: string) =>
        apiUrl(
          `/api/panel/v1/sessions/${encodeURIComponent(publicSessionId)}`,
        ),
    },
  },
} as const;

/**
 * Resuelve el origen público del frontend a partir de la petición entrante.
 * Prioriza las cabeceras de proxy inverso (x-forwarded-*) y luego Host, de
 * forma que el redirect use la URL real con la que navega el usuario en lugar
 * del fallback estático NEXUS_FRONTEND_BASE_URL (que por defecto es localhost).
 */
function resolveRequestOrigin(request: Request): string | null {
  const headers = new Headers(request.headers);
  const forwardedHost = firstHeaderValue(headers.get("x-forwarded-host"));
  const host = forwardedHost ?? headers.get("host");
  if (!host) {
    return null;
  }

  const forwardedProto = headers.get("x-forwarded-proto");
  let proto = firstHeaderValue(forwardedProto) ?? "";
  if (!proto) {
    try {
      proto = new URL(request.url).protocol.replace(/:$/, "");
    } catch {
      proto = "http";
    }
  }

  return `${proto}://${host}`;
}

function firstHeaderValue(value: string | null) {
  return value
    ?.split(",")[0]
    ?.trim() || null;
}

function configuredAllowedOrigins() {
  const rawAllowedOrigins = process.env.NEXUS_ALLOWED_DEV_ORIGINS ?? "";
  const configuredFrontend = new URL(CONFIGURED_FRONTEND_ORIGIN);
  const allowedOrigins = new Set<string>([configuredFrontend.origin]);
  const allowedHosts = new Set<string>([
    configuredFrontend.host,
    configuredFrontend.hostname,
  ]);

  for (const rawOrigin of rawAllowedOrigins.split(",")) {
    const value = rawOrigin.trim();
    if (!value) {
      continue;
    }

    try {
      const allowedUrl = new URL(value);
      allowedOrigins.add(allowedUrl.origin);
      allowedHosts.add(allowedUrl.host);
      allowedHosts.add(allowedUrl.hostname);
    } catch {
      allowedHosts.add(value);
    }
  }

  return { allowedOrigins, allowedHosts };
}

function allowedFrontendOrigin(origin: string) {
  try {
    const originUrl = new URL(origin);
    const { allowedOrigins, allowedHosts } = configuredAllowedOrigins();
    return (
      allowedOrigins.has(originUrl.origin) ||
      allowedHosts.has(originUrl.host) ||
      allowedHosts.has(originUrl.hostname)
    );
  } catch {
    return false;
  }
}

function safeContinuePath(continuePath: string, frontendBaseUrl: string) {
  try {
    const continueUrl = new URL(continuePath, frontendBaseUrl);
    if (continueUrl.origin !== new URL(frontendBaseUrl).origin) {
      return new URL("/dashboard", frontendBaseUrl).toString();
    }

    return continueUrl.toString();
  } catch {
    return new URL("/dashboard", frontendBaseUrl).toString();
  }
}

export function buildPanelLoginUrl(
  continuePath = "/dashboard",
  options: { request?: Request } = {},
) {
  let frontendBaseUrl: string;
  if (typeof window !== "undefined") {
    // Navegador: el origen ya es el real.
    frontendBaseUrl = window.location.origin;
  } else if (options.request) {
    // Servidor: derivar del Host/forwarded de la petición, no de la env.
    const requestOrigin = resolveRequestOrigin(options.request);
    frontendBaseUrl =
      requestOrigin && allowedFrontendOrigin(requestOrigin)
        ? requestOrigin
        : CONFIGURED_FRONTEND_BASE_URL;
  } else {
    frontendBaseUrl = CONFIGURED_FRONTEND_BASE_URL;
  }

  const continueUrl = safeContinuePath(continuePath, frontendBaseUrl);
  const loginUrl = new URL(apiRoutes.panel.session.login);
  loginUrl.searchParams.set("continue", continueUrl);
  return loginUrl.toString();
}
