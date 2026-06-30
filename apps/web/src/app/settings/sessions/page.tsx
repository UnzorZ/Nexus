"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import {
  useCurrentAccount,
  usePanelSessions,
  useRevokeAllPanelSessions,
  useRevokePanelSession,
} from "@/features/session/queries";
import { type PanelSessionSummary } from "@/features/session/api";
import { NexusApiError } from "@/lib/api/client";
import { toMessage } from "@/lib/api/errors";
import { buildPanelLoginUrl } from "@/lib/auth/continue-url";

function formatInstant(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("es-ES", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function describeUserAgent(userAgent: string | null | undefined): string {
  if (!userAgent || userAgent.trim() === "") {
    return "Dispositivo o navegador desconocido";
  }
  return userAgent;
}

const SESSIONS_MESSAGES = {
  codes: {
    csrf_rejected:
      "El formulario expiró o falta protección CSRF. Recarga e inténtalo de nuevo.",
  },
};

const LOGIN_URL = buildPanelLoginUrl("/settings/sessions");

function isUnauthorized(err: unknown): boolean {
  return err instanceof NexusApiError && err.status === 401;
}

export default function SessionsPage() {
  const router = useRouter();
  const accountQ = useCurrentAccount();
  const sessionsQ = usePanelSessions();
  const revokeM = useRevokePanelSession();
  const revokeAllM = useRevokeAllPanelSessions();

  const sessions = sessionsQ.data ?? [];
  const ready = !accountQ.isLoading;
  const pendingId = revokeM.isPending ? revokeM.variables ?? null : null;
  const revokingAll = revokeAllM.isPending;

  const rawErr =
    accountQ.error ?? sessionsQ.error ?? revokeM.error ?? revokeAllM.error;
  const error =
    rawErr && !isUnauthorized(rawErr) ? toMessage(rawErr, SESSIONS_MESSAGES) : null;

  // Sin sesión (401 en /me resuelve null, o 401 directo en /sessions) → al login.
  useEffect(() => {
    if (accountQ.data === null || isUnauthorized(sessionsQ.error)) {
      window.location.href = LOGIN_URL;
    }
  }, [accountQ.data, sessionsQ.error]);

  async function handleRevoke(session: PanelSessionSummary) {
    try {
      await revokeM.mutateAsync(session.id);
      if (session.current) {
        router.push("/login");
      }
      // revokeM.onSuccess invalida /sessions → la lista se refresca sola.
    } catch {
      /* error via revokeM.error */
    }
  }

  async function handleRevokeAll() {
    try {
      await revokeAllM.mutateAsync();
      router.push("/login");
    } catch {
      /* error via revokeAllM.error */
    }
  }

  if (!ready) {
    return (
      <main className="min-h-screen bg-[#f7f4ef] px-6 py-10 text-[#181612]">
        <p className="font-mono text-sm text-[#796d5f]">
          Comprobando sesión...
        </p>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#f7f4ef] px-6 py-10 text-[#181612]">
      <section className="mx-auto flex min-h-[80vh] max-w-4xl flex-col justify-center">
        <div className="mb-10 flex items-center justify-between gap-4">
          <p className="font-mono text-xs uppercase tracking-[0.18em] text-[#796d5f]">
            Nexus control plane
          </p>
          <button
            type="button"
            onClick={handleRevokeAll}
            disabled={revokingAll || pendingId !== null || sessions.length === 0}
            className="rounded-full border border-[#d8d0c4] bg-white/70 px-4 py-2 text-sm font-medium text-[#3d352b] shadow-sm transition hover:border-[#8a2f2f] hover:bg-[#fff5f5] hover:text-[#8a2f2f] disabled:cursor-not-allowed disabled:opacity-60"
          >
            {revokingAll ? "Cerrando sesiones..." : "Cerrar todas las sesiones"}
          </button>
        </div>

        <h1 className="max-w-2xl text-5xl font-semibold tracking-tight sm:text-6xl">
          Sesiones
        </h1>
        <p className="mt-4 max-w-xl text-base leading-7 text-[#5e554a]">
          Estas son las sesiones activas de tu cuenta en el panel de Nexus. Puedes
          revocar cualquier sesión de forma individual o cerrar todas a la vez.
        </p>

        {error ? (
          <p className="mt-6 rounded-xl border border-[#d8b4b4] bg-[#fff5f5] px-4 py-3 text-sm text-[#8a2f2f]">
            {error}
          </p>
        ) : null}

        <ul className="mt-10 space-y-4">
          {sessions.map((session) => (
            <li
              key={session.id}
              className="rounded-2xl border border-[#d8d0c4] bg-white/70 p-5 shadow-sm"
            >
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    {session.current ? (
                      <span className="rounded-full border border-[#181612] bg-[#181612] px-2.5 py-0.5 text-xs font-medium text-[#f7f4ef]">
                        Actual
                      </span>
                    ) : null}
                    <span className="font-mono text-xs uppercase tracking-[0.14em] text-[#796d5f]">
                      {describeUserAgent(session.userAgent)}
                    </span>
                  </div>
                  <dl className="grid grid-cols-1 gap-x-8 gap-y-1 text-sm text-[#5e554a] sm:grid-cols-2">
                    <div>
                      <dt className="inline font-medium text-[#3d352b]">
                        Creada:{" "}
                      </dt>
                      <dd className="inline">
                        {formatInstant(session.createdAt)}
                      </dd>
                    </div>
                    <div>
                      <dt className="inline font-medium text-[#3d352b]">
                        Último acceso:{" "}
                      </dt>
                      <dd className="inline">
                        {formatInstant(session.lastAccessedAt)}
                      </dd>
                    </div>
                    <div>
                      <dt className="inline font-medium text-[#3d352b]">
                        Expira:{" "}
                      </dt>
                      <dd className="inline">{formatInstant(session.expiresAt)}</dd>
                    </div>
                  </dl>
                </div>
                <button
                  type="button"
                  onClick={() => handleRevoke(session)}
                  disabled={pendingId !== null || revokingAll}
                  className="rounded-full border border-[#d8d0c4] bg-white/80 px-4 py-2 text-sm font-medium text-[#3d352b] transition hover:border-[#8a2f2f] hover:bg-[#fff5f5] hover:text-[#8a2f2f] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {pendingId === session.id ? "Revocando..." : "Revocar"}
                </button>
              </div>
            </li>
          ))}
          {sessions.length === 0 && error === null ? (
            <li className="rounded-2xl border border-dashed border-[#d8d0c4] bg-white/40 p-6 text-center text-sm text-[#796d5f]">
              No hay sesiones activas.
            </li>
          ) : null}
        </ul>
      </section>
    </main>
  );
}
