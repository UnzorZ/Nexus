"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft, ShieldAlert } from "lucide-react";
import {
  useInstanceSettings,
  useInstanceSmtpSettings,
  useInstanceStatus,
  useSaveInstanceDefaultModules,
  useSaveInstanceHeartbeat,
  useSaveInstanceRegistration,
  useSaveInstanceSmtpSettings,
  useTestInstanceSmtpConnection,
} from "@/features/instance/queries";
import type { SmtpConnectionCheck } from "@/features/instance/api";
import { useCurrentAccount } from "@/features/session/queries";
import {
  EMPTY_SMTP_FORM,
  SmtpSettingsForm,
  type SmtpFormValue,
} from "@/components/ui/smtp-settings-form";
import { MODULE_CATALOG } from "@/components/dashboard/modules/catalog";
import { Panel, StatusBadge } from "@/components/dashboard/shared";
import { ThemedLogo } from "@/components/ui/theme-toggle";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toMessage } from "@/lib/api/errors";
import { buildPanelLoginUrl } from "@/lib/auth/continue-url";

const LOGIN_URL = buildPanelLoginUrl("/instance-settings");

/**
 * Espejo del enum del backend {@code NexusModule.DEFAULT_ENABLED}. Se usa como
 * línea base cuando el operador no ha definido un override (defaultModules=null).
 * Si cambia el catálogo del enum, actualizar también aquí.
 */
const SYSTEM_DEFAULT_MODULES = [
  "identity",
  "permissions",
  "registry",
  "audit",
  "config",
  "metrics",
  "notify",
  "vault",
];

const MESSAGES = {
  permission: "You don't have permission to view instance settings.",
  forbidden: "Your session expired. Reload the page and try again.",
  codes: {
    instance_admin_required:
      "This action requires the instance admin role.",
    smtp_unsafe_host: "That SMTP host is not allowed (internal/blocked address).",
    registration_closed: "Registration is closed on this instance.",
  },
};

export default function InstanceSettingsPage() {
  const accountQ = useCurrentAccount();
  const smtpQ = useInstanceSmtpSettings();
  const statusQ = useInstanceStatus();
  const settingsQ = useInstanceSettings();
  const saveM = useSaveInstanceSmtpSettings();
  const testM = useTestInstanceSmtpConnection();
  const registrationM = useSaveInstanceRegistration();
  const modulesM = useSaveInstanceDefaultModules();
  const heartbeatM = useSaveInstanceHeartbeat();

  const [smtpForm, setSmtpForm] = useState<SmtpFormValue>(EMPTY_SMTP_FORM);
  const [connResult, setConnResult] = useState<SmtpConnectionCheck | null>(null);
  const [smtpError, setSmtpError] = useState<string | null>(null);

  // Módulos por defecto (edición local).
  const [moduleKeys, setModuleKeys] = useState<string[] | null>(null);
  // Heartbeat (edición local).
  const [hbInterval, setHbInterval] = useState("");
  const [hbTimeout, setHbTimeout] = useState("");

  const account = accountQ.data ?? null;
  const smtp = smtpQ.data ?? null;
  const status = statusQ.data ?? null;
  const settings = settingsQ.data ?? null;
  const ready = !accountQ.isLoading;

  // accountQ.data es `undefined` mientras carga y `null` solo si no hay sesión.
  // Distinguirlas evita echar al login durante la carga (el `?? null` colapsa
  // ambos estados en null). Patrón de app/settings/sessions.
  useEffect(() => {
    if (accountQ.data === null) {
      window.location.href = LOGIN_URL;
    } else if (accountQ.data && !accountQ.data.instanceAdmin) {
      window.location.href = "/";
    }
  }, [accountQ.data]);

  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect */
    if (smtp) {
      setSmtpForm({
        host: smtp.host ?? "",
        port: smtp.port ? String(smtp.port) : "",
        username: smtp.username ?? "",
        from: smtp.from ?? "",
        password: "",
        tlsMode: smtp.tlsMode ?? "PUBLIC",
        trustedCaPem: "",
      });
      setConnResult(null);
    }
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [smtp]);

  // Sincroniza la edición local de módulos/heartbeat con el estado persistido.
  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect */
    if (settings) {
      setModuleKeys(settings.defaultModules ?? SYSTEM_DEFAULT_MODULES);
      setHbInterval(
        settings.heartbeat.intervalSeconds == null
          ? ""
          : String(settings.heartbeat.intervalSeconds),
      );
      setHbTimeout(
        settings.heartbeat.timeoutSeconds == null
          ? ""
          : String(settings.heartbeat.timeoutSeconds),
      );
    }
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [settings]);

  async function saveSmtp() {
    setSmtpError(null);
    setConnResult(null);
    const port = Number(smtpForm.port);
    try {
      await saveM.mutateAsync({
        host: smtpForm.host.trim(),
        port: Number.isFinite(port) && port > 0 ? port : 587,
        username: smtpForm.username.trim(),
        from: smtpForm.from.trim(),
        password: smtpForm.password,
        tlsMode: smtpForm.tlsMode,
        trustedCaPem:
          smtpForm.tlsMode === "PINNED" ? smtpForm.trustedCaPem : undefined,
      });
    } catch (error) {
      setSmtpError(toMessage(error, MESSAGES));
    }
  }

  async function testConn() {
    setConnResult(null);
    testM.reset();
    try {
      setConnResult(await testM.mutateAsync());
    } catch {
      setConnResult(null);
    }
  }

  function toggleModule(key: string) {
    setModuleKeys((prev) => {
      const current = prev ?? SYSTEM_DEFAULT_MODULES;
      return current.includes(key)
        ? current.filter((k) => k !== key)
        : [...current, key];
    });
  }

  async function saveModules(reset: boolean) {
    try {
      await modulesM.mutateAsync(reset ? null : (moduleKeys ?? []));
    } catch {
      /* error via modulesM.error */
    }
  }

  async function saveHeartbeat(clear: boolean) {
    const interval = clear ? null : Number(hbInterval);
    const timeout = clear ? null : Number(hbTimeout);
    if (!clear && (!Number.isFinite(interval) || !Number.isFinite(timeout))) {
      return;
    }
    try {
      await heartbeatM.mutateAsync({
        intervalSeconds: clear ? null : interval,
        timeoutSeconds: clear ? null : timeout,
      });
    } catch {
      /* error via heartbeatM.error */
    }
  }

  if (!ready || !account) {
    return (
      <main className="min-h-screen bg-background px-6 py-10 text-foreground">
        <p className="font-mono text-sm text-muted-foreground">
          Comprobando sesión...
        </p>
      </main>
    );
  }

  if (!account.instanceAdmin) {
    return null;
  }

  const modulesDirty =
    settings &&
    JSON.stringify(moduleKeys ?? SYSTEM_DEFAULT_MODULES) !==
      JSON.stringify(settings.defaultModules ?? SYSTEM_DEFAULT_MODULES);

  return (
    <main className="min-h-screen bg-background px-6 py-10 text-foreground">
      <div className="mx-auto flex max-w-4xl flex-col gap-8">
        <header className="flex flex-col gap-4">
          <div className="flex items-center justify-between gap-4">
            <ThemedLogo height={26} />
            <Link
              href="/projects"
              className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition hover:text-foreground"
            >
              <ArrowLeft size={15} />
              Back to dashboard
            </Link>
          </div>
          <div>
            <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
              Operator
            </p>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight">
              Instance settings
            </h1>
            <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
              Operator-wide configuration: the SMTP relay, who can register,
              which modules new projects start with, and the heartbeat defaults.
            </p>
          </div>
        </header>

        {/* Email delivery (SMTP) */}
        <Panel
          title="Email delivery (SMTP)"
          description="The instance relay. Every project sends through this by default unless it sets its own override."
          action={
            <StatusBadge tone={smtp?.host && smtp?.from ? "emerald" : "slate"} dot>
              {smtp?.host && smtp?.from ? "Configured" : "Not configured"}
            </StatusBadge>
          }
        >
          {smtpQ.isLoading ? (
            <p className="text-sm text-muted-foreground">Loading…</p>
          ) : smtpQ.error ? (
            <p className="text-sm text-destructive">
              {toMessage(smtpQ.error, MESSAGES)}
            </p>
          ) : (
            <SmtpSettingsForm
              value={smtpForm}
              onChange={setSmtpForm}
              passwordConfigured={smtp?.passwordConfigured ?? false}
              trustedCaConfigured={smtp?.trustedCaConfigured ?? false}
              savedConfigured={!!smtp?.host && !!smtp?.from}
              onSave={saveSmtp}
              onTestConnection={testConn}
              saving={saveM.isPending}
              testing={testM.isPending}
              testResult={connResult}
              testError={testM.error}
              error={smtpError}
              canEdit
            />
          )}
        </Panel>

        {/* Registration policy */}
        <Panel
          title="Registration"
          description="Whether new accounts can self-register. The first account always becomes the instance admin; closing this only blocks new sign-ups."
          action={
            <StatusBadge tone={settings?.registrationOpen ? "emerald" : "amber"} dot>
              {settings?.registrationOpen ? "Open" : "Closed"}
            </StatusBadge>
          }
        >
          {settingsQ.isLoading ? (
            <p className="text-sm text-muted-foreground">Loading…</p>
          ) : settingsQ.error ? (
            <p className="text-sm text-destructive">
              {toMessage(settingsQ.error, MESSAGES)}
            </p>
          ) : (
            <div className="flex flex-wrap items-center gap-3">
              <Button
                size="sm"
                variant={settings?.registrationOpen ? "default" : "outline"}
                disabled={registrationM.isPending || settings?.registrationOpen}
                onClick={() => registrationM.mutate(true)}
              >
                Open registration
              </Button>
              <Button
                size="sm"
                variant={!settings?.registrationOpen ? "default" : "outline"}
                disabled={registrationM.isPending || !settings?.registrationOpen}
                onClick={() => registrationM.mutate(false)}
              >
                Close registration
              </Button>
              {registrationM.isError ? (
                <span className="text-xs text-destructive">
                  {toMessage(registrationM.error, MESSAGES)}
                </span>
              ) : null}
            </div>
          )}
        </Panel>

        {/* Module defaults */}
        <Panel
          title="Module defaults"
          description="Modules enabled by default for new projects. Toggle what a fresh project starts with."
          action={
            <div className="flex items-center gap-2">
              {settings?.defaultModules == null ? (
                <StatusBadge tone="slate">system defaults</StatusBadge>
              ) : (
                <StatusBadge tone="blue">custom override</StatusBadge>
              )}
            </div>
          }
        >
          {settingsQ.isLoading ? (
            <p className="text-sm text-muted-foreground">Loading…</p>
          ) : (
            <div className="flex flex-col gap-4">
              <div className="flex flex-wrap gap-2">
                {MODULE_CATALOG.map((m) => {
                  const on = (moduleKeys ?? SYSTEM_DEFAULT_MODULES).includes(m.key);
                  return (
                    <button
                      key={m.key}
                      type="button"
                      onClick={() => toggleModule(m.key)}
                      className={
                        on
                          ? "rounded-full border border-emerald-500/40 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-700 transition dark:text-emerald-300"
                          : "rounded-full border border-border bg-muted/40 px-3 py-1 text-xs font-medium text-muted-foreground transition hover:border-border"
                      }
                    >
                      {m.name}
                    </button>
                  );
                })}
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <Button
                  size="sm"
                  onClick={() => saveModules(false)}
                  disabled={modulesM.isPending || !modulesDirty}
                >
                  {modulesM.isPending ? "Saving…" : "Save defaults"}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => saveModules(true)}
                  disabled={modulesM.isPending || settings?.defaultModules == null}
                  title="Reset to the system catalog defaults"
                >
                  Reset to system defaults
                </Button>
                {modulesM.isError ? (
                  <span className="text-xs text-destructive">
                    {toMessage(modulesM.error, MESSAGES)}
                  </span>
                ) : null}
              </div>
            </div>
          )}
        </Panel>

        {/* Heartbeat defaults */}
        <Panel
          title="Heartbeat defaults"
          description="Instance-wide liveness thresholds for the registry. A project can still override its own."
          action={
            settings?.heartbeat.intervalSeconds == null ? (
              <StatusBadge tone="slate">env default</StatusBadge>
            ) : (
              <StatusBadge tone="blue">custom override</StatusBadge>
            )
          }
        >
          {settingsQ.isLoading ? (
            <p className="text-sm text-muted-foreground">Loading…</p>
          ) : (
            <div className="flex flex-col gap-4">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="hb-interval">Interval (seconds, ONLINE window)</Label>
                  <Input
                    id="hb-interval"
                    type="number"
                    placeholder="30"
                    value={hbInterval}
                    onChange={(e) => setHbInterval(e.target.value)}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="hb-timeout">Timeout (seconds, OFFLINE after)</Label>
                  <Input
                    id="hb-timeout"
                    type="number"
                    placeholder="90"
                    value={hbTimeout}
                    onChange={(e) => setHbTimeout(e.target.value)}
                  />
                </div>
              </div>
              <p className="text-xs text-muted-foreground">
                Require 1 ≤ interval ≤ timeout. Leave both blank and clear to fall
                back to the env default.
              </p>
              <div className="flex flex-wrap items-center gap-2">
                <Button
                  size="sm"
                  onClick={() => saveHeartbeat(false)}
                  disabled={
                    heartbeatM.isPending ||
                    !hbInterval.trim() ||
                    !hbTimeout.trim()
                  }
                >
                  {heartbeatM.isPending ? "Saving…" : "Save heartbeat defaults"}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => saveHeartbeat(true)}
                  disabled={
                    heartbeatM.isPending ||
                    settings?.heartbeat.intervalSeconds == null
                  }
                >
                  Clear (use env default)
                </Button>
                {heartbeatM.isError ? (
                  <span className="text-xs text-destructive">
                    {toMessage(heartbeatM.error, MESSAGES)}
                  </span>
                ) : null}
              </div>
            </div>
          )}
        </Panel>

        {/* Access & security (read-only status) */}
        <Panel
          title="Access & security"
          description="Read-only status of deploy-time settings. Change these via environment variables and restart."
        >
          <StatusGrid status={status} loading={statusQ.isLoading} error={statusQ.error} />
        </Panel>
      </div>
    </main>
  );
}

function StatusGrid({
  status,
  loading,
  error,
}: {
  status: InstanceStatusLike | null;
  loading: boolean;
  error: unknown;
}) {
  if (loading) {
    return <p className="text-sm text-muted-foreground">Loading…</p>;
  }
  if (error) {
    return <p className="text-sm text-destructive">{toMessage(error, MESSAGES)}</p>;
  }
  if (!status) {
    return null;
  }
  const devKey = status.vaultMasterKey.status === "dev-default";
  const ephemeral = status.jwtKeystore.status === "ephemeral";
  return (
    <dl className="grid grid-cols-1 gap-x-8 gap-y-3 text-sm sm:grid-cols-2">
      <Row label="Session timeout">
        <span className="font-mono">{status.session.timeout}</span>
      </Row>
      <Row label="Session cookie">
        <span>
          SameSite <span className="font-mono">{status.session.cookieSameSite}</span>
          {" · "}
          {status.session.cookieSecure ? "Secure" : "not Secure"}
          {" · "}
          {status.session.cookieHttpOnly ? "HttpOnly" : "not HttpOnly"}
        </span>
      </Row>
      <Row label="Vault master key">
        <StatusBadge tone={devKey ? "amber" : "emerald"}>
          {status.vaultMasterKey.status}
        </StatusBadge>
        {devKey ? (
          <span className="ml-2 inline-flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400">
            <ShieldAlert size={13} />
            not safe for prod
          </span>
        ) : null}
      </Row>
      <Row label="JWT keystore">
        <StatusBadge tone={ephemeral ? "amber" : "emerald"}>
          {status.jwtKeystore.status}
        </StatusBadge>
        {ephemeral ? (
          <span className="ml-2 inline-flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400">
            <ShieldAlert size={13} />
            ephemeral keys
          </span>
        ) : null}
        {status.jwtKeystore.keystoreLocation ? (
          <span className="ml-2 truncate font-mono text-xs text-muted-foreground">
            {status.jwtKeystore.keystoreLocation}
          </span>
        ) : null}
      </Row>
      <Row label="Frontend base URL">
        <span className="font-mono text-xs">{status.frontendBaseUrl || "—"}</span>
      </Row>
      <Row label="Allowed origins (CORS)">
        <span className="font-mono text-xs">{status.allowedOrigins || "—"}</span>
      </Row>
    </dl>
  );
}

type InstanceStatusLike = {
  session: { timeout: string; cookieSameSite: string; cookieSecure: boolean; cookieHttpOnly: boolean };
  vaultMasterKey: { status: string };
  jwtKeystore: { status: string; keystoreLocation: string | null };
  frontendBaseUrl: string;
  allowedOrigins: string;
};

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </dt>
      <dd className="text-foreground">{children}</dd>
    </div>
  );
}
