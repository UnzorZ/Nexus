"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { QRCodeSVG } from "qrcode.react";
import { ShieldAlert, ShieldCheck } from "lucide-react";
import {
  beginPanelMfaEnrollment,
  confirmPanelMfaEnrollment,
  disablePanelMfa,
  fetchPanelMfaStatus,
  type PanelMfaEnrollment,
} from "@/features/mfa/api";
import { fetchCurrentAccount } from "@/features/session/api";
import { queryKeys } from "@/lib/api/queryKeys";
import { buildPanelLoginUrl } from "@/lib/auth/continue-url";
import { NexusApiError } from "@/lib/api/client";

type Step = "idle" | "qr" | "recovery" | "enabled";

const LOGIN_URL = buildPanelLoginUrl("/settings/security");

function isUnauthorized(err: unknown): boolean {
  return err instanceof NexusApiError && err.status === 401;
}

export default function SecurityPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const [ready, setReady] = useState(false);
  const [step, setStep] = useState<Step>("idle");
  const [enrollment, setEnrollment] = useState<PanelMfaEnrollment | null>(null);
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const status = useQuery({
    queryKey: queryKeys.mfa.status(),
    queryFn: () => fetchPanelMfaStatus(),
    enabled: ready,
  });

  useEffect(() => {
    let cancelled = false;
    fetchCurrentAccount().then((account) => {
      if (cancelled) return;
      if (!account) {
        window.location.href = LOGIN_URL;
        return;
      }
      setReady(true);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const enrollM = useMutation({
    mutationFn: () => beginPanelMfaEnrollment(),
    onSuccess: (e) => {
      setEnrollment(e);
      setStep("qr");
      setError(null);
    },
    onError: (err) =>
      setError(err instanceof NexusApiError ? err.message : "No se pudo iniciar la inscripción."),
  });

  const verifyM = useMutation({
    mutationFn: (code: string) => confirmPanelMfaEnrollment(code),
    onSuccess: (codes) => {
      setRecoveryCodes(codes);
      setStep("recovery");
      setError(null);
      qc.invalidateQueries({ queryKey: queryKeys.mfa.status() });
    },
    onError: (err) =>
      setError(err instanceof NexusApiError ? err.message : "Código incorrecto."),
  });

  const disableM = useMutation({
    mutationFn: (code: string) => disablePanelMfa(code),
    onSuccess: () => {
      setStep("idle");
      setError(null);
      qc.invalidateQueries({ queryKey: queryKeys.mfa.status() });
    },
    onError: (err) =>
      setError(err instanceof NexusApiError ? err.message : "No se pudo desactivar la MFA."),
  });

  function handleVerify(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const code = String(new FormData(e.currentTarget).get("code") ?? "").trim();
    if (code) void verifyM.mutateAsync(code);
  }
  function handleDisable(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const code = String(new FormData(e.currentTarget).get("code") ?? "").trim();
    if (code) void disableM.mutateAsync(code);
  }

  useEffect(() => {
    if (isUnauthorized(status.error)) {
      window.location.href = LOGIN_URL;
    }
  }, [status.error]);

  if (!ready || !status.data) {
    return (
      <main className="min-h-screen bg-[#f7f4ef] px-6 py-10 text-[#181612]">
        <p className="font-mono text-sm text-[#796d5f]">Comprobando sesión...</p>
      </main>
    );
  }

  const enabled = status.data.enabled || step === "recovery";

  return (
    <main className="min-h-screen bg-[#f7f4ef] px-6 py-10 text-[#181612]">
      <section className="mx-auto flex min-h-[80vh] max-w-3xl flex-col justify-center">
        <p className="font-mono text-xs uppercase tracking-[0.18em] text-[#796d5f]">
          Nexus control plane
        </p>
        <h1 className="mt-2 text-4xl font-semibold tracking-tight sm:text-5xl">
          Two-factor authentication
        </h1>
        <p className="mt-4 max-w-xl text-base leading-7 text-[#5e554a]">
          Protege tu cuenta del panel con un segundo factor (app de autenticador TOTP).
          Tras activarlo necesitarás un código de 6 dígitos en cada inicio de sesión.
        </p>

        {error ? (
          <p className="mt-6 rounded-xl border border-[#d8b4b4] bg-[#fff5f5] px-4 py-3 text-sm text-[#8a2f2f]">
            {error}
          </p>
        ) : null}

        {enabled && step !== "recovery" ? (
          <div className="mt-8 space-y-4">
            <div className="flex items-center gap-2 rounded-xl border border-[#cdd9c2] bg-[#f3f7ef] px-4 py-3 text-sm text-[#3f5a2b]">
              <ShieldCheck size={16} className="shrink-0" />
              <span>
                Two-factor activado. Recovery codes restantes:{" "}
                {status.data.recoveryCodesRemaining}.
              </span>
            </div>
            <form onSubmit={handleDisable} className="space-y-2">
              <label htmlFor="dcode" className="text-sm font-medium text-[#3d352b]">
                Desactivar two-factor (confirma con un código)
              </label>
              <input
                id="dcode"
                name="code"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                placeholder="000000"
                disabled={disableM.isPending}
                className="h-11 w-full rounded-lg border border-[#d8d0c4] bg-white px-3 font-mono text-[#181612] focus:outline-none focus:ring-2 focus:ring-[#8a2f2f]/30"
              />
              <button
                type="submit"
                disabled={disableM.isPending}
                className="rounded-full border border-[#d8d0c4] bg-white/80 px-4 py-2 text-sm font-medium text-[#3d352b] transition hover:border-[#8a2f2f] hover:bg-[#fff5f5] hover:text-[#8a2f2f] disabled:opacity-60"
              >
                {disableM.isPending ? "Desactivando…" : "Desactivar two-factor"}
              </button>
            </form>
          </div>
        ) : step === "qr" && enrollment ? (
          <div className="mt-8 space-y-5">
            <p className="text-sm text-[#5e554a]">
              Escanea este QR con tu app de autenticador (Google Authenticator, Authy,
              1Password…) e introduce el código de 6 dígitos.
            </p>
            <div className="flex justify-center rounded-xl border border-[#d8d0c4] bg-white p-4">
              <QRCodeSVG value={enrollment.otpauthUri} size={192} />
            </div>
            <details className="text-xs text-[#796d5f]">
              <summary className="cursor-pointer">¿No puedes escanear? Introduce esta clave a mano</summary>
              <p className="mt-2 break-all font-mono">{enrollment.secret}</p>
            </details>
            <form onSubmit={handleVerify} className="space-y-2">
              <label htmlFor="code" className="text-sm font-medium text-[#3d352b]">
                Código de verificación
              </label>
              <input
                id="code"
                name="code"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                autoFocus
                placeholder="000000"
                disabled={verifyM.isPending}
                className="h-12 w-full rounded-lg border border-[#d8d0c4] bg-white px-3 text-center font-mono text-lg tracking-[0.4em] text-[#181612] focus:outline-none focus:ring-2 focus:ring-violet-500/30"
              />
              <button
                type="submit"
                disabled={verifyM.isPending}
                className="h-12 w-full rounded-xl bg-[#181612] px-4 text-sm font-semibold text-[#f7f4ef] transition hover:bg-[#3d352b] disabled:opacity-60"
              >
                {verifyM.isPending ? "Verificando…" : "Verificar y activar"}
              </button>
            </form>
          </div>
        ) : step === "recovery" && recoveryCodes ? (
          <div className="mt-8 space-y-4">
            <div className="flex items-center gap-2 rounded-xl border border-[#cdd9c2] bg-[#f3f7ef] px-4 py-3 text-sm text-[#3f5a2b]">
              <ShieldCheck size={16} className="shrink-0" />
              <span>Two-factor activado.</span>
            </div>
            <p className="text-sm text-[#5e554a]">
              Guarda estos recovery codes en un sitio seguro. Cada uno sirve una vez en
              lugar de un código TOTP si pierdes tu dispositivo.
            </p>
            <div className="grid grid-cols-2 gap-1.5 rounded-xl border border-[#d8d0c4] bg-white p-4 font-mono text-sm text-[#181612]">
              {recoveryCodes.map((c) => (
                <div key={c}>{c}</div>
              ))}
            </div>
            <button
              type="button"
              onClick={() => {
                setStep("enabled");
                setRecoveryCodes(null);
              }}
              className="h-11 w-full rounded-xl bg-[#181612] px-4 text-sm font-semibold text-[#f7f4ef] transition hover:bg-[#3d352b]"
            >
              Ya he guardado mis recovery codes
            </button>
          </div>
        ) : (
          <div className="mt-8 space-y-4">
            <p className="text-sm text-[#5e554a]">
              Añade un segundo factor (TOTP) para proteger tu cuenta del panel. Tras
              activarlo necesitarás un código de tu app en cada inicio de sesión.
            </p>
            <button
              type="button"
              onClick={() => enrollM.mutate()}
              disabled={enrollM.isPending}
              className="h-12 w-full rounded-xl bg-[#181612] px-4 text-sm font-semibold text-[#f7f4ef] transition hover:bg-[#3d352b] disabled:opacity-60"
            >
              {enrollM.isPending ? "Iniciando…" : "Configurar two-factor"}
            </button>
          </div>
        )}

        <div className="mt-8 flex items-center gap-4 text-sm">
          <Link href="/settings/sessions" className="font-medium text-[#5e554a] hover:text-[#181612]">
            ← Sesiones
          </Link>
          <Link href="/projects" className="font-medium text-[#5e554a] hover:text-[#181612]">
            Volver al panel
          </Link>
        </div>
      </section>
    </main>
  );
}
