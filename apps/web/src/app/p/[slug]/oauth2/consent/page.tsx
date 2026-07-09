"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, use } from "react";
import { motion } from "motion/react";
import { Check, ShieldCheck } from "lucide-react";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { AuthShell } from "@/app/login/AuthShell";

/**
 * Pantalla de consentimiento OAuth del usuario final (sustituye al flujo Thymeleaf). El
 * backend redirige aquí ({@code ConsentController}) reenviando {@code client_id},
 * {@code client_name} (nombre legible, si es un cliente de proyecto), {@code scope},
 * {@code state}, {@code action} (URL absoluta del API {@code /p/{slug}/oauth2/authorize})
 * y {@code _csrf} (token enmascarado que el {@code CsrfFilter} del AS valida al reenviarlo).
 *
 * <p>El envío es un <b>POST nativo</b> del navegador a {@code action} (no fetch): así la
 * cookie de sesión viaja al host del API, el AS registra el consentimiento y redirige al
 * {@code redirect_uri} del cliente con el code, y el navegador sigue la 302 de forma
 * natural.</p>
 */
export default function EndUserConsentPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  return (
    <Suspense fallback={<AuthShell mode="minimal" />}>
      <EndUserConsentScreen slugPromise={params} />
    </Suspense>
  );
}

function EndUserConsentScreen({
  slugPromise,
}: {
  slugPromise: Promise<{ slug: string }>;
}) {
  const { slug } = use(slugPromise);
  const searchParams = useSearchParams();
  const clientId = searchParams.get("client_id") ?? "";
  const clientName = searchParams.get("client_name") ?? "";
  const state = searchParams.get("state") ?? "";
  const action = searchParams.get("action") ?? "";
  const csrf = searchParams.get("_csrf") ?? "";
  const scopeParam = searchParams.get("scope") ?? "";
  const scopes = scopeParam
    .split(" ")
    .map((s) => s.trim())
    .filter(Boolean);

  // Nombre legible si el backend lo resolvió (cliente de proyecto); si no, client_id.
  const displayApp = clientName || clientId;
  const initial = displayApp.charAt(0).toUpperCase() || "?";

  if (!action || !csrf || !clientId) {
    return (
      <AuthShell mode="minimal">
        <motion.p
          variants={fadeUp}
          initial="hidden"
          animate="show"
          className="text-sm text-muted-foreground"
        >
          This authorization request is no longer valid. Please start again.
        </motion.p>
      </AuthShell>
    );
  }

  return (
    <AuthShell mode="minimal">
      {/* Identidad de la app que pide acceso: chip con inicial + nombre legible. */}
      <motion.div
        variants={fadeUp}
        initial="hidden"
        animate="show"
        className="flex items-center gap-3.5"
      >
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-lg font-bold text-primary">
          {initial}
        </div>
        <div className="min-w-0">
          <h1 className="truncate text-xl font-semibold tracking-tight text-foreground">
            {displayApp}
          </h1>
          <p className="mt-0.5 text-sm text-muted-foreground">
            wants to access the{" "}
            <strong className="font-semibold text-foreground">{slug}</strong> project
          </p>
        </div>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={SPRING_SNAPPY}
        className="mt-6"
      >
        <Card>
          <CardHeader>
            <CardTitle className="font-heading text-sm">
              {displayApp} will be able to:
            </CardTitle>
          </CardHeader>
          <CardContent>
            {scopes.length > 0 ? (
              <ul className="space-y-2.5">
                {scopes.map((scope) => (
                  <li
                    key={scope}
                    className="flex items-start gap-2.5 text-sm text-muted-foreground"
                  >
                    <Check
                      size={16}
                      className="mt-0.5 shrink-0 text-emerald-600 dark:text-emerald-400"
                    />
                    <span>{describeScope(scope)}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-muted-foreground">
                No specific permissions requested.
              </p>
            )}
          </CardContent>
        </Card>
      </motion.div>

      <p className="mt-4 text-xs leading-relaxed text-muted-foreground">
        This does not give the application your password or session credentials. You can
        revoke access at any time from your project settings.
      </p>

      {/* client_id en mono como detalle de verificación (IdP orientado a devs). */}
      <p className="mt-2 font-mono text-[11px] text-muted-foreground/70">
        Client ID: {clientId}
      </p>

      {/* POST nativo al host del API: el AS registra el consentimiento y redirige al
          redirect_uri del cliente. El _csrf reenviado valida contra el CsrfFilter del AS. */}
      <form className="mt-5 flex gap-3" method="POST" action={action}>
        <input type="hidden" name="client_id" value={clientId} />
        <input type="hidden" name="state" value={state} />
        <input type="hidden" name="_csrf" value={csrf} />
        {scopes.map((scope) => (
          <input key={scope} type="hidden" name="scope" value={scope} />
        ))}
        <Button asChild variant="outline" className="h-11 flex-1">
          <Link href={`/p/${encodeURIComponent(slug)}/login`}>Cancel</Link>
        </Button>
        <Button type="submit" className="h-11 flex-1">
          Authorize
        </Button>
      </form>

      <div className="mt-6 flex items-center justify-center gap-2 text-xs text-muted-foreground">
        <ShieldCheck size={14} className="shrink-0" />
        <span>
          Authorizing via{" "}
          <strong className="font-medium text-foreground">{slug}</strong> identity.
        </span>
      </div>
    </AuthShell>
  );
}

/** Traduce scopes OIDC estándar a descripciones legibles; desconocidos se muestran tal cual. */
function describeScope(scope: string): string {
  switch (scope) {
    case "openid":
      return "Authenticate you (sign you in)";
    case "profile":
      return "Read your profile (name, username, display name)";
    case "email":
      return "Read your email address";
    case "offline_access":
      return "Access your account when you are not present (refresh tokens)";
    default:
      return `Access: ${scope}`;
  }
}
