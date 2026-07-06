"use client";

import { useRef, useState } from "react";
import { Eye, EyeOff, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { StatusBadge } from "@/components/dashboard/shared";
import { toMessage } from "@/lib/api/errors";

export type SmtpTlsMode = "PUBLIC" | "PINNED";

/** Valores editables del formulario SMTP (compartido instancia/proyecto). */
export type SmtpFormValue = {
  host: string;
  port: string;
  username: string;
  from: string;
  password: string;
  tlsMode: SmtpTlsMode;
  trustedCaPem: string;
};

export const EMPTY_SMTP_FORM: SmtpFormValue = {
  host: "",
  port: "587",
  username: "",
  from: "",
  password: "",
  tlsMode: "PUBLIC",
  trustedCaPem: "",
};

/** Resultado de una comprobación de conexión (forma compartida instancia/proyecto). */
export type SmtpConnectionResult = { ok: boolean; message: string };

const DEFAULT_MESSAGES = {
  forbidden: "Your session expired. Reload the page and try again.",
  codes: {},
};

/**
 * Formulario SMTP reutilizable (relay de instancia o por proyecto). Es
 * controlado: el padre posee el estado ({@link SmtpFormValue}) y los hooks de
 * guardado/test; este componente sólo renderiza los campos, el modo TLS
 * (ADR-0013), la CA pinneada, el tutorial Let's Encrypt, la contraseña y los
 * botones. {@link https://datatracker.ietf.org/doc/html/rfc7807 RFC 7807}
 * readable errors via {@link toMessage}.
 */
export function SmtpSettingsForm({
  value,
  onChange,
  passwordConfigured,
  trustedCaConfigured,
  savedConfigured,
  onSave,
  onTestConnection,
  saving,
  testing,
  testResult,
  testError,
  error,
  canEdit,
}: {
  value: SmtpFormValue;
  onChange: (next: SmtpFormValue) => void;
  /** Si ya hay una contraseña guardada (nunca se devuelve del backend). */
  passwordConfigured: boolean;
  /** Si ya hay una CA pinneada guardada. */
  trustedCaConfigured: boolean;
  /** Si existe configuración guardada (host+from) — habilita "Test connection". */
  savedConfigured: boolean;
  onSave: () => void;
  onTestConnection: () => void;
  saving: boolean;
  testing: boolean;
  testResult: SmtpConnectionResult | null;
  testError: unknown;
  error: string | null;
  canEdit: boolean;
}) {
  const [showPassword, setShowPassword] = useState(false);
  const caFileInputRef = useRef<HTMLInputElement>(null);

  if (!canEdit) {
    return (
      <div className="flex flex-col gap-2 text-sm text-muted-foreground">
        <span>Host: {value.host || "—"}</span>
        <span>From: {value.from || "—"}</span>
      </div>
    );
  }

  function onUploadCa(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const text = typeof reader.result === "string" ? reader.result : "";
      onChange({ ...value, trustedCaPem: text });
    };
    reader.readAsText(file);
    event.target.value = "";
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="smtp-host">Host</Label>
          <Input
            id="smtp-host"
            placeholder="smtp.example.com"
            value={value.host}
            onChange={(e) => onChange({ ...value, host: e.target.value })}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="smtp-port">Port</Label>
          <Input
            id="smtp-port"
            type="number"
            placeholder="587"
            value={value.port}
            onChange={(e) => onChange({ ...value, port: e.target.value })}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="smtp-user">Username</Label>
          <Input
            id="smtp-user"
            placeholder="notifications@example.com"
            value={value.username}
            onChange={(e) => onChange({ ...value, username: e.target.value })}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="smtp-from">From</Label>
          <Input
            id="smtp-from"
            placeholder="Project <notifications@example.com>"
            value={value.from}
            onChange={(e) => onChange({ ...value, from: e.target.value })}
          />
        </div>
      </div>
      {/* TLS trust mode (ADR-0013) */}
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="smtp-tls">TLS trust</Label>
        <Select
          value={value.tlsMode}
          onValueChange={(v) => onChange({ ...value, tlsMode: v as SmtpTlsMode })}
        >
          <SelectTrigger id="smtp-tls" size="sm">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="PUBLIC">
              Public CA (Gmail, SendGrid, Let&apos;s Encrypt…)
            </SelectItem>
            <SelectItem value="PINNED">
              Self-signed / private CA (pin a certificate)
            </SelectItem>
          </SelectContent>
        </Select>
        <p className="text-xs text-muted-foreground">
          {value.tlsMode === "PUBLIC"
            ? "The server certificate is verified against public CAs. Use this for any provider with a public certificate."
            : "Trust only the CA you upload below — for a self-signed or internal SMTP server."}
        </p>
      </div>

      {value.tlsMode === "PUBLIC" ? (
        <details className="rounded border border-border bg-muted/30 p-3 text-xs">
          <summary className="cursor-pointer font-medium text-foreground">
            No public cert yet? Get one free with Let&apos;s Encrypt
          </summary>
          <div className="mt-2 flex flex-col gap-2 text-muted-foreground">
            <p>
              If your SMTP server uses a self-signed or Cloudflare-Origin
              certificate, Nexus can&apos;t verify it in this mode. Issue a free
              public certificate via DNS-01 (no port to open):
            </p>
            <ol className="ml-4 list-decimal space-y-1.5">
              <li>
                On your mail server, install{" "}
                <code className="font-mono">certbot</code> and the Cloudflare DNS
                plugin
                (<code className="font-mono">python3-certbot-dns-cloudflare</code>).
              </li>
              <li>
                Create a Cloudflare API token with{" "}
                <code className="font-mono">Zone · DNS · Edit</code> for your
                domain.
              </li>
              <li>
                Issue the certificate:
                <pre className="mt-1 overflow-x-auto rounded bg-background/60 p-2 font-mono">
{`certbot certonly --dns-cloudflare \\
  --dns-cloudflare-credentials ~/.cloudflare.ini \\
  -d mail.yourdomain.com`}
                </pre>
              </li>
              <li>
                Point your SMTP server at{" "}
                <code className="font-mono">
                  /etc/letsencrypt/live/mail.yourdomain.com/
                </code>{" "}
                (<code className="font-mono">fullchain.pem</code> +{" "}
                <code className="font-mono">privkey.pem</code>) and reload it.
              </li>
              <li>
                Verify (you should see <em>Verification: OK</em>):
                <pre className="mt-1 overflow-x-auto rounded bg-background/60 p-2 font-mono">
{`openssl s_client -starttls smtp \\
  -connect mail.yourdomain.com:25 \\
  -servername mail.yourdomain.com -brief`}
                </pre>
              </li>
            </ol>
            <p>
              No Cloudflare? Use HTTP-01 (needs port 80 open):{" "}
              <code className="font-mono">
                certbot certonly --standalone -d mail.yourdomain.com
              </code>
              .
            </p>
          </div>
        </details>
      ) : null}

      {value.tlsMode === "PINNED" ? (
        <div className="flex flex-col gap-1.5">
          <div className="flex items-center justify-between gap-2">
            <Label htmlFor="smtp-ca">Trusted CA certificate (PEM)</Label>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => caFileInputRef.current?.click()}
            >
              <Upload size={14} />
              Upload .pem/.crt
            </Button>
            <input
              ref={caFileInputRef}
              type="file"
              accept=".pem,.crt,.cer,application/x-pem-file,text/plain"
              className="hidden"
              onChange={onUploadCa}
            />
          </div>
          <Textarea
            id="smtp-ca"
            value={value.trustedCaPem}
            onChange={(e) => onChange({ ...value, trustedCaPem: e.target.value })}
            rows={6}
            placeholder={
              "-----BEGIN CERTIFICATE-----\n…\n-----END CERTIFICATE-----"
            }
            className="font-mono text-xs"
          />
          <p className="text-xs text-muted-foreground">
            {trustedCaConfigured
              ? "A CA is saved. Paste or upload to replace it (leave untouched to keep)."
              : "Paste the PEM of your SMTP server's CA. Required to save in this mode."}
          </p>
        </div>
      ) : null}

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="smtp-pass">Password</Label>
        <div className="relative">
          <Input
            id="smtp-pass"
            type={showPassword ? "text" : "password"}
            className="pr-10"
            placeholder={
              passwordConfigured
                ? "•••••••• (leave blank to keep current)"
                : "smtp password"
            }
            value={value.password}
            onChange={(e) => onChange({ ...value, password: e.target.value })}
          />
          <button
            type="button"
            className="absolute right-1 top-1/2 inline-flex size-5 -translate-y-1/2 items-center justify-center rounded-sm text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/30"
            aria-label={showPassword ? "Hide password" : "Show password"}
            aria-pressed={showPassword}
            aria-controls="smtp-pass"
            tabIndex={-1}
            onClick={() => setShowPassword((s) => !s)}
          >
            {showPassword ? <EyeOff size={14} /> : <Eye size={14} />}
          </button>
        </div>
      </div>
      {error ? <p className="text-xs text-destructive">{error}</p> : null}
      {testResult ? (
        <div
          className={
            testResult.ok
              ? "rounded border border-emerald-500/40 bg-emerald-500/10 p-3"
              : "rounded border border-destructive/40 bg-destructive/10 p-3"
          }
        >
          <StatusBadge tone={testResult.ok ? "emerald" : "red"} dot>
            {testResult.ok ? "Connection OK" : "Connection failed"}
          </StatusBadge>
          <p className="mt-1.5 break-words font-mono text-xs text-muted-foreground">
            {testResult.message}
          </p>
        </div>
      ) : null}
      {testError ? (
        <p className="text-xs text-destructive">{toMessage(testError, DEFAULT_MESSAGES)}</p>
      ) : null}
      <div className="flex flex-wrap items-center justify-end gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onTestConnection}
          disabled={testing || !savedConfigured}
          title={
            !savedConfigured
              ? "Save your SMTP settings first."
              : "Test the saved connection (TLS verified + AUTH)"
          }
        >
          {testing ? "Testing…" : "Test connection"}
        </Button>
        <Button
          size="sm"
          onClick={onSave}
          disabled={
            saving ||
            !value.host.trim() ||
            !value.from.trim() ||
            (value.tlsMode === "PINNED" &&
              !value.trustedCaPem.trim() &&
              !trustedCaConfigured)
          }
        >
          {saving ? "Saving…" : "Save SMTP settings"}
        </Button>
      </div>
    </div>
  );
}
