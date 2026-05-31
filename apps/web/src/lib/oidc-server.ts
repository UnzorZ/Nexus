import { createHash, randomBytes } from "crypto";

export const AUTH_BROWSER_BASE_URL =
  process.env.NEXUS_AUTH_BASE_URL ?? "http://localhost:8080";
export const AUTH_INTERNAL_BASE_URL =
  process.env.NEXUS_AUTH_INTERNAL_BASE_URL ?? "http://host.docker.internal:8080";
export const CLIENT_ID = process.env.NEXUS_OIDC_CLIENT_ID ?? "oidc-client";
export const CLIENT_SECRET = process.env.NEXUS_OIDC_CLIENT_SECRET ?? "secret";
export const FRONTEND_BASE_URL =
  process.env.NEXUS_FRONTEND_BASE_URL ?? "http://localhost:3000";
export const SCOPE = process.env.NEXUS_OIDC_SCOPE ?? "openid";

export const OIDC_LOGIN_COOKIE = "nexus_oidc_login";
export const NEXUS_SESSION_COOKIE = "nexus_session";

const PKCE_CHARSET =
  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";

export type StoredOidcLogin = {
  state: string;
  nonce: string;
  codeVerifier: string;
  redirectUri: string;
  createdAt: number;
};

export type NexusSession = {
  accessToken: string;
  idToken?: string;
  refreshToken?: string;
  tokenType: string;
  expiresIn?: number;
  createdAt: number;
};

export function randomUrlSafeString(length = 64) {
  const bytes = randomBytes(length);

  return Array.from(bytes, (byte) => PKCE_CHARSET[byte % PKCE_CHARSET.length])
    .join("");
}

export function codeChallengeFromVerifier(codeVerifier: string) {
  return createHash("sha256")
    .update(codeVerifier)
    .digest("base64url");
}

export function createLoginCookieValue(login: StoredOidcLogin) {
  return Buffer.from(JSON.stringify(login), "utf8").toString("base64url");
}

export function parseLoginCookieValue(value: string) {
  try {
    return JSON.parse(Buffer.from(value, "base64url").toString("utf8")) as
      | StoredOidcLogin
      | null;
  } catch {
    return null;
  }
}

export function createSessionCookieValue(session: NexusSession) {
  return Buffer.from(JSON.stringify(session), "utf8").toString("base64url");
}

export function parseSessionCookieValue(value: string) {
  try {
    return JSON.parse(Buffer.from(value, "base64url").toString("utf8")) as
      | NexusSession
      | null;
  } catch {
    return null;
  }
}

export function buildRedirectUri() {
  return `${FRONTEND_BASE_URL}/login/oauth2/code/${CLIENT_ID}`;
}
