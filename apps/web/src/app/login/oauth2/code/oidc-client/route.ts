import { NextRequest, NextResponse } from "next/server";
import {
  AUTH_INTERNAL_BASE_URL,
  CLIENT_ID,
  CLIENT_SECRET,
  FRONTEND_BASE_URL,
  NEXUS_SESSION_COOKIE,
  OIDC_LOGIN_COOKIE,
  createSessionCookieValue,
  parseLoginCookieValue,
  type NexusSession,
} from "@/lib/oidc-server";

type TokenResponse = {
  access_token: string;
  id_token?: string;
  refresh_token?: string;
  token_type: string;
  expires_in?: number;
};

export async function GET(request: NextRequest) {
  const requestUrl = new URL(request.url);
  const code = requestUrl.searchParams.get("code");
  const state = requestUrl.searchParams.get("state");
  const error = requestUrl.searchParams.get("error");
  const errorDescription = requestUrl.searchParams.get("error_description");
  const storedLogin = parseLoginCookieValue(
    request.cookies.get(OIDC_LOGIN_COOKIE)?.value ?? "",
  );

  if (error) {
    return redirectToLoginError(errorDescription ?? error);
  }

  if (!code || !state || !storedLogin || state !== storedLogin.state) {
    if (request.cookies.get(NEXUS_SESSION_COOKIE)?.value) {
      return redirectToDashboard();
    }

    return redirectToLoginError("Invalid authorization callback.");
  }

  let tokenResponse: TokenResponse;
  try {
    tokenResponse = await exchangeCodeForTokens(code, storedLogin);
  } catch (unknownError) {
    return redirectToLoginError(
      unknownError instanceof Error
        ? unknownError.message
        : "Token exchange failed.",
    );
  }
  const session: NexusSession = {
    accessToken: tokenResponse.access_token,
    idToken: tokenResponse.id_token,
    refreshToken: tokenResponse.refresh_token,
    tokenType: tokenResponse.token_type,
    expiresIn: tokenResponse.expires_in,
    createdAt: Date.now(),
  };

  const response = redirectToDashboard();
  response.cookies.delete(OIDC_LOGIN_COOKIE);
  response.cookies.set(NEXUS_SESSION_COOKIE, createSessionCookieValue(session), {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: 60 * 60,
  });

  return response;
}

async function exchangeCodeForTokens(
  code: string,
  storedLogin: { codeVerifier: string; redirectUri: string },
) {
  const tokenUrl = new URL("/oauth2/token", AUTH_INTERNAL_BASE_URL);
  const body = new URLSearchParams({
    grant_type: "authorization_code",
    code,
    redirect_uri: storedLogin.redirectUri,
    code_verifier: storedLogin.codeVerifier,
  });

  let response: Response;
  try {
    response = await fetch(tokenUrl, {
      method: "POST",
      headers: {
        Authorization: `Basic ${Buffer.from(`${CLIENT_ID}:${CLIENT_SECRET}`).toString("base64")}`,
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body,
      cache: "no-store",
    });
  } catch (unknownError) {
    const cause =
      unknownError instanceof Error && "cause" in unknownError
        ? String(unknownError.cause)
        : "unknown cause";
    const message =
      unknownError instanceof Error ? unknownError.message : "unknown error";
    throw new Error(`Token endpoint fetch failed: ${message}; ${cause}`);
  }

  if (!response.ok) {
    throw new Error(`Token exchange failed with status ${response.status}`);
  }

  return (await response.json()) as TokenResponse;
}

function redirectToDashboard() {
  return NextResponse.redirect(new URL("/dashboard", FRONTEND_BASE_URL));
}

function redirectToLoginError(message: string) {
  const loginErrorUrl = new URL("/login/error", FRONTEND_BASE_URL);
  loginErrorUrl.searchParams.set("message", message);
  return NextResponse.redirect(loginErrorUrl);
}
