"use client";

import Link from "next/link";
import { FormEvent, Suspense, use, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { motion } from "motion/react";
import { CheckCircle2, ShieldAlert } from "lucide-react";
import { NexusApiError } from "@/lib/api/client";
import { requestEndUserPasswordReset } from "@/features/end-user/api";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AuthShell } from "@/app/login/AuthShell";

/**
 * Solicitud de reseteo de contraseña del usuario final (sustituye al flujo Thymeleaf).
 * Anti-enumeración: la API responde siempre 200, así que se muestra el mismo mensaje
 * exista o no la cuenta.
 */
export default function EndUserPasswordResetPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = use(params);
  return (
    <Suspense fallback={<AuthShell mode="minimal" />}>
      <PasswordResetRequestScreen slug={slug} />
    </Suspense>
  );
}

function PasswordResetRequestScreen({ slug }: { slug: string }) {
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const resetM = useMutation({
    mutationFn: (email: string) => requestEndUserPasswordReset(slug, email),
  });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const formData = new FormData(event.currentTarget);
    const email = String(formData.get("email") ?? "").trim();
    if (!email) {
      setError("Please enter your email address.");
      return;
    }
    try {
      await resetM.mutateAsync(email);
      setSent(true);
    } catch (err) {
      setError(
        err instanceof NexusApiError ? err.message : "Could not connect to the server.",
      );
    }
  }

  if (sent) {
    return (
      <AuthShell mode="minimal">
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
          className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-5"
        >
          <CheckCircle2 className="text-emerald-600 dark:text-emerald-400" />
          <h2 className="mt-3 text-lg font-semibold text-foreground">Check your email</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            If an account exists for that email, we&apos;ve sent a link to reset your
            password.
          </p>
        </motion.div>
        <Link
          href={`/p/${encodeURIComponent(slug)}/login`}
          className="mt-6 block text-center text-sm font-medium text-violet-600 hover:underline dark:text-violet-400"
        >
          Back to sign in
        </Link>
      </AuthShell>
    );
  }

  return (
    <AuthShell mode="minimal">
      <motion.header variants={fadeUp} initial="hidden" animate="show">
        <h1 className="text-2xl font-bold tracking-tight text-foreground">
          Reset your password
        </h1>
        <p className="mt-1.5 text-sm text-muted-foreground">
          Enter your email and we&apos;ll send you a reset link.
        </p>
      </motion.header>

      {error ? (
        <motion.div
          role="alert"
          initial={{ opacity: 0, y: -4 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
          className="mt-5 flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive"
        >
          <ShieldAlert size={16} className="shrink-0" />
          <span>{error}</span>
        </motion.div>
      ) : null}

      <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
        <div className="space-y-1.5">
          <Label htmlFor="email" className="text-sm font-medium">
            Email
          </Label>
          <Input
            id="email"
            name="email"
            type="email"
            className="h-12 bg-white px-3 focus-visible:ring-violet-500/30 dark:bg-input/30"
            placeholder="you@example.com"
            autoComplete="email"
            inputMode="email"
            autoFocus
            required
            disabled={resetM.isPending}
          />
        </div>
        <Button
          type="submit"
          className="h-12 w-full rounded-xl bg-violet-600 text-white hover:bg-violet-700 dark:bg-violet-500 dark:hover:bg-violet-600"
          disabled={resetM.isPending}
        >
          {resetM.isPending ? "Sending…" : "Send reset link"}
        </Button>
      </form>
    </AuthShell>
  );
}
