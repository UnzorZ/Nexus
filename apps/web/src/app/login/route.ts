import { NextResponse } from "next/server";
import {
  AUTH_BROWSER_BASE_URL,
  CLIENT_ID,
  OIDC_LOGIN_COOKIE,
  SCOPE,
  buildRedirectUri,
  codeChallengeFromVerifier,
  createLoginCookieValue,
  randomUrlSafeString,
  type StoredOidcLogin,
} from "@/lib/oidc-server";

export function GET() {
  const state = randomUrlSafeString(32);
  const nonce = randomUrlSafeString(32);
  const codeVerifier = randomUrlSafeString(96);
  const redirectUri = buildRedirectUri();
  const codeChallenge = codeChallengeFromVerifier(codeVerifier);

  const storedLogin: StoredOidcLogin = {
    state,
    nonce,
    codeVerifier,
    redirectUri,
    createdAt: Date.now(),
  };

  const authorizeUrl = new URL("/oauth2/authorize", AUTH_BROWSER_BASE_URL);
  authorizeUrl.searchParams.set("response_type", "code");
  authorizeUrl.searchParams.set("client_id", CLIENT_ID);
  authorizeUrl.searchParams.set("scope", SCOPE);
  authorizeUrl.searchParams.set("redirect_uri", redirectUri);
  authorizeUrl.searchParams.set("state", state);
  authorizeUrl.searchParams.set("nonce", nonce);
  authorizeUrl.searchParams.set("code_challenge", codeChallenge);
  authorizeUrl.searchParams.set("code_challenge_method", "S256");

  const response = NextResponse.redirect(authorizeUrl);
  response.cookies.set(OIDC_LOGIN_COOKIE, createLoginCookieValue(storedLogin), {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: 10 * 60,
  });

  return response;
}
