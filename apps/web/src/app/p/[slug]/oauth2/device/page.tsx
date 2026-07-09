"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, use } from "react";
import { motion } from "motion/react";
import { Check, Smartphone, ShieldCheck } from "lucide-react";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { AuthShell } from "@/app/login/AuthShell";

/**
 * Pantalla de verificación del Device Authorization Grant (RFC 8628). El backend redirige
 * aquí ({@code DeviceVerificationController}) reenviando {@code client_id},
 * {@code client_name}, {@code state}, {@code user_code}, {@code scope}, {@code action}
 * (URL absoluta del API {@code /p/{slug}/oauth2/device_verification}) y {@code _csrf}.
 *
 * <p>El usuario ya está autenticado en el realm del proyecto (sesión end-user); aquí
 * confirma el código que le muestra el dispositivo y aprueba. El envío es un <b>POST
 * nativo</b> del navegador a {@code action}: SAS liga el device_code al usuario y el
 * dispositivo, que está sondeando /oauth2/token, recibe los tokens.</p>
 */
export default function EndUserDevicePage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  return (
    <Suspense fallback={<AuthShell mode="minimal" />}>
      <EndUserDeviceScreen slugPromise={params} />
    </Suspense>
  );
}

function EndUserDeviceScreen({
  slugPromise,
}: {
  slugPromise: Promise<{ slug: string }>;
}) {
  const { slug } = use(slugPromise);
  const searchParams = useSearchParams();
  const clientId = searchParams.get("client_id") ?? "";
  const clientName = searchParams.get("client_name") ?? "";
  const state = searchParams.get("state") ?? "";
  const userCode = searchParams.get("user_code") ?? "";
  const action = searchParams.get("action") ?? "";
  const csrf = searchParams.get("_csrf") ?? "";
  const scopeParam = searchParams.get("scope") ?? "";
  const scopes = scopeParam
    .split(" ")
    .map((s) => s.trim())
    .filter(Boolean);

  const displayApp = clientName || clientId;

  if (!action || !csrf || !clientId || !userCode) {
    return (
      <AuthShell mode="minimal">
        <motion.p
          variants={fadeUp}
          initial="hidden"
          animate="show"
          className="text-sm text-muted-foreground"
        >
          This device authorization is no longer valid. Please request a new code from the
          device.
        </motion.p>
      </AuthShell>
    );
  }

  return (
    <AuthShell mode="minimal">
      <motion.div
        variants={fadeUp}
        initial="hidden"
        animate="show"
        className="flex items-center gap-3.5"
      >
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
          <Smartphone size={22} />
        </div>
        <div className="min-w-0">
          <h1 className="truncate text-xl font-semibold tracking-tight text-foreground">
            Authorize device
          </h1>
          <p className="mt-0.5 text-sm text-muted-foreground">
            <strong className="font-semibold text-foreground">{displayApp}</strong> wants
            to access the{" "}
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
              Confirm the code on your device
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">
              Make sure this matches the code shown on the device requesting access.
            </p>
            <p className="mt-3 rounded-lg border bg-muted/40 px-4 py-3 text-center font-mono text-2xl font-semibold tracking-[0.2em] text-foreground">
              {userCode}
            </p>
          </CardContent>
        </Card>
      </motion.div>

      {scopes.length > 0 ? (
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
          className="mt-4"
        >
          <Card>
            <CardHeader>
              <CardTitle className="font-heading text-sm">
                {displayApp} will be able to:
              </CardTitle>
            </CardHeader>
            <CardContent>
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
            </CardContent>
          </Card>
        </motion.div>
      ) : null}

      <p className="mt-4 text-xs leading-relaxed text-muted-foreground">
        Approving signs the device in to {slug}. You can revoke it any time by revoking
        that user&apos;s tokens from the project panel.
      </p>

      <p className="mt-2 font-mono text-[11px] text-muted-foreground/70">
        Client ID: {clientId}
      </p>

      {/* POST nativo al host del API: SAS liga el device_code al usuario y el dispositivo
          recibe los tokens al sondear /oauth2/token. El _csrf reenviado valida. */}
      <form className="mt-5 flex gap-3" method="POST" action={action}>
        <input type="hidden" name="client_id" value={clientId} />
        <input type="hidden" name="state" value={state} />
        <input type="hidden" name="user_code" value={userCode} />
        <input type="hidden" name="_csrf" value={csrf} />
        {scopes.map((scope) => (
          <input key={scope} type="hidden" name="scope" value={scope} />
        ))}
        <Button asChild variant="outline" className="h-11 flex-1">
          <Link href={`/p/${encodeURIComponent(slug)}/account`}>Cancel</Link>
        </Button>
        <Button type="submit" className="h-11 flex-1">
          Approve device
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
