"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  fetchCurrentAccount,
  fetchPanelSessions,
  revokeAllPanelSessions,
  revokePanelSession,
  type PanelSessionSummary,
} from "@/features/session/api";
import { NexusApiError } from "@/lib/api/client";
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

export default function SessionsPage() {
  const router = useRouter();
  const [ready, setReady] = useState(false);
  const [sessions, setSessions] = useState<PanelSessionSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [revokingAll, setRevokingAll] = useState(false);

  const refreshSessions = useCallback(async () => {
    try {
      setSessions(await fetchPanelSessions());
      setError(null);
    } catch (loadError) {
      if (loadError instanceof NexusApiError && loadError.status === 401) {
        window.location.href = buildPanelLoginUrl("/settings/sessions");
        return;
      }
      if (loadError instanceof NexusApiError) {
        setError(loadError.message);
      } else {
        setError("No se pudieron cargar las sesiones.");
      }
    }
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const currentAccount = await fetchCurrentAccount();
        if (cancelled) {
          return;
        }

        if (!currentAccount) {
          window.location.href = buildPanelLoginUrl("/settings/sessions");
          return;
        }

        const currentSessions = await fetchPanelSessions();
        if (cancelled) {
          return;
        }

        setSessions(currentSessions);
        setError(null);
      } catch (loadError) {
        if (cancelled) {
          return;
        }

        if (loadError instanceof NexusApiError && loadError.status === 401) {
          window.location.href = buildPanelLoginUrl("/settings/sessions");
          return;
        }

        if (loadError instanceof NexusApiError) {
          setError(loadError.message);
        } else {
          setError("No se pudo comprobar la sesión del panel.");
        }
      } finally {
        if (!cancelled) {
          setReady(true);
        }
      }
    }

    load();

    return () => {
      cancelled = true;
    };
  }, []);

  async function handleRevoke(session: PanelSessionSummary) {
    setError(null);
    setPendingId(session.id);

    try {
      await revokePanelSession(session.id);
      if (session.current) {
        router.push("/login");
        return;
      }
      await refreshSessions();
    } catch (revokeError) {
      if (revokeError instanceof NexusApiError) {
        setError(revokeError.message);
      } else {
        setError("No se pudo revocar la sesión.");
      }
    } finally {
      setPendingId(null);
    }
  }

  async function handleRevokeAll() {
    setError(null);
    setRevokingAll(true);

    try {
      await revokeAllPanelSessions();
      router.push("/login");
    } catch (revokeError) {
      if (revokeError instanceof NexusApiError) {
        setError(revokeError.message);
      } else {
        setError("No se pudieron cerrar todas las sesiones.");
      }
    } finally {
      setRevokingAll(false);
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
