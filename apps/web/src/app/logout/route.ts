import { NextResponse } from "next/server";
import {
  FRONTEND_BASE_URL,
  NEXUS_SESSION_COOKIE,
  OIDC_LOGIN_COOKIE,
} from "@/lib/oidc-server";

export async function POST() {
  const response = NextResponse.redirect(new URL("/", FRONTEND_BASE_URL), {
    status: 303,
  });

  response.cookies.delete(NEXUS_SESSION_COOKIE);
  response.cookies.delete(OIDC_LOGIN_COOKIE);

  return response;
}
