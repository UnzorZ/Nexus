"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { createNexusAccount } from "@/features/accounts/api";
import { NexusApiError } from "@/lib/api/client";

export default function RegisterPage() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [isPending, setIsPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsPending(true);

    const formData = new FormData(event.currentTarget);
    const email = String(formData.get("email") ?? "").trim();
    const password = String(formData.get("password") ?? "");
    const displayName = String(formData.get("displayName") ?? "").trim();

    if (!email || !password || !displayName) {
      setError("Completa email, contraseña y nombre visible.");
      setIsPending(false);
      return;
    }

    if (password.length < 8) {
      setError("La contraseña debe tener al menos 8 caracteres.");
      setIsPending(false);
      return;
    }

    try {
      await createNexusAccount({ email, password, displayName });
      router.push("/login");
    } catch (submitError) {
      if (submitError instanceof NexusApiError) {
        setError(submitError.message);
      } else {
        setError("No se pudo conectar con la API de Nexus.");
      }
    } finally {
      setIsPending(false);
    }
  }

  return (
    <main className="min-h-screen bg-[#f7f4ef] px-6 py-10 text-[#181612]">
      <section className="mx-auto flex min-h-[80vh] max-w-xl flex-col justify-center">
        <p className="mb-3 font-mono text-xs uppercase tracking-[0.18em] text-[#796d5f]">
          Nexus control plane
        </p>
        <h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">
          Crear cuenta Nexus
        </h1>
        <p className="mt-4 text-base leading-7 text-[#5e554a]">
          Registra una cuenta para acceder al panel. Después podrás iniciar sesión
          en la API con sesión HTTP segura.
        </p>

        <form onSubmit={handleSubmit} className="mt-8 space-y-5">
          <label className="block space-y-2">
            <span className="text-sm font-medium text-[#3d352b]">Email</span>
            <input
              name="email"
              type="email"
              autoComplete="email"
              required
              className="w-full rounded-xl border border-[#d8d0c4] bg-white/80 px-4 py-3 text-[#181612] outline-none transition focus:border-[#181612]"
            />
          </label>

          <label className="block space-y-2">
            <span className="text-sm font-medium text-[#3d352b]">
              Nombre visible
            </span>
            <input
              name="displayName"
              type="text"
              autoComplete="name"
              required
              className="w-full rounded-xl border border-[#d8d0c4] bg-white/80 px-4 py-3 text-[#181612] outline-none transition focus:border-[#181612]"
            />
          </label>

          <label className="block space-y-2">
            <span className="text-sm font-medium text-[#3d352b]">Contraseña</span>
            <input
              name="password"
              type="password"
              autoComplete="new-password"
              minLength={8}
              required
              className="w-full rounded-xl border border-[#d8d0c4] bg-white/80 px-4 py-3 text-[#181612] outline-none transition focus:border-[#181612]"
            />
          </label>

          {error ? (
            <p className="rounded-xl border border-[#d8b4b4] bg-[#fff5f5] px-4 py-3 text-sm text-[#8a2f2f]">
              {error}
            </p>
          ) : null}

          <button
            type="submit"
            disabled={isPending}
            className="inline-flex h-12 w-full items-center justify-center border border-[#181612] bg-[#181612] px-5 text-sm font-medium text-[#f7f4ef] transition hover:bg-transparent hover:text-[#181612] disabled:cursor-not-allowed disabled:opacity-60"
          >
            {isPending ? "Creando cuenta..." : "Crear cuenta"}
          </button>
        </form>

        <p className="mt-6 text-sm leading-6 text-[#5e554a]">
          ¿Ya tienes cuenta?{" "}
          <Link href="/login" className="font-medium text-[#181612] underline">
            Iniciar sesión
          </Link>
        </p>
      </section>
    </main>
  );
}
