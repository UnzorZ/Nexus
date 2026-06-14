"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  fetchCurrentAccount,
  logoutPanelSession,
} from "@/features/session/api";
import type { NexusAccount } from "@/features/accounts/api";
import { NexusApiError } from "@/lib/api/client";
import { buildPanelLoginUrl } from "@/lib/api/routes";

export default function DashboardPage() {
  const router = useRouter();
  const [account, setAccount] = useState<NexusAccount | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    fetchCurrentAccount()
      .then((currentAccount) => {
        if (cancelled) {
          return;
        }

        if (!currentAccount) {
          window.location.href = buildPanelLoginUrl("/dashboard");
          return;
        }

        setAccount(currentAccount);
      })
      .catch((loadError) => {
        if (cancelled) {
          return;
        }

        if (loadError instanceof NexusApiError) {
          setError(loadError.message);
        } else {
          setError("No se pudo comprobar la sesión del panel.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  async function handleLogout() {
    try {
      await logoutPanelSession();
      router.push("/");
    } catch (logoutError) {
      if (logoutError instanceof NexusApiError) {
        setError(logoutError.message);
      } else {
        setError("No se pudo cerrar la sesión.");
      }
    }
  }

  if (loading) {
    return (
      <main className="min-h-screen bg-[#f7f4ef] px-6 py-10 text-[#181612]">
        <p className="font-mono text-sm text-[#796d5f]">Comprobando sesión...</p>
      </main>
    );
  }

  if (error) {
    return (
      <main className="min-h-screen bg-[#f7f4ef] px-6 py-10 text-[#181612]">
        <p className="text-sm text-[#8a2f2f]">{error}</p>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#f7f4ef] px-6 py-10 text-[#181612]">
      <section className="mx-auto flex min-h-[80vh] max-w-4xl flex-col justify-center">
        <div className="mb-12 flex items-center justify-between gap-4">
          <p className="font-mono text-xs uppercase tracking-[0.18em] text-[#796d5f]">
            Nexus control plane
          </p>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-full border border-[#d8d0c4] bg-white/70 px-4 py-2 text-sm font-medium text-[#3d352b] shadow-sm transition hover:border-[#181612] hover:bg-[#181612] hover:text-[#f7f4ef]"
          >
            Cerrar sesión
          </button>
        </div>
        <h1 className="max-w-2xl text-5xl font-semibold tracking-tight sm:text-7xl">
          Dashboard
        </h1>
        <p className="mt-6 max-w-xl text-base leading-7 text-[#5e554a]">
          Sesión iniciada como {account?.displayName ?? account?.email} mediante
          la sesión HTTP del panel.
          {account?.instanceAdmin ? " Cuenta administradora de instancia." : null}
        </p>
      </section>
    </main>
  );
}
