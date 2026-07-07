"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { FormEvent, Suspense, use, useEffect, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { motion } from "motion/react";
import { CheckCircle2, ShieldAlert } from "lucide-react";
import { NexusApiError } from "@/lib/api/client";
import { resendEndUserVerification, verifyEndUserEmail } from "@/features/end-user/api";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AuthShell } from "@/app/login/AuthShell";

type VerifyState = { kind: "verifying" } | { kind: "verified" } | { kind: "resend" };

/**
 * Verificación de email del usuario final (sustituye al flujo Thymeleaf). Si llega con
 * `?token=` (desde el email) verifica automáticamente; si llega con `?email=` (desde el
 * login) muestra el formulario de reenvío.
 */
export default function EndUserVerifyEmailPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  return (
    <Suspense fallback={<AuthShell mode="minimal" />}>
      <EndUserVerifyEmailScreen slugPromise={params} />
    </Suspense>
  );
}

function EndUserVerifyEmailScreen({
  slugPromise,
}: {
  slugPromise: Promise<{ slug: string }>;
}) {
  const { slug } = use(slugPromise);
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const presetEmail = searchParams.get("email") ?? "";
  const [state] = useState<VerifyState>(
    token ? { kind: "verifying" } : { kind: "resend" },
  );

  const verifyM = useMutation({
    mutationFn: (t: string) => verifyEndUserEmail(slug, t),
  });

  useEffect(() => {
    if (token && state.kind === "verifying") {
      verifyM.mutate(token);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  if (verifyM.isSuccess || state.kind === "verified") {
    return (
      <AuthShell mode="minimal">
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
          className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-5 text-center"
        >
          <CheckCircle2 className="mx-auto text-emerald-600 dark:text-emerald-400" />
          <h2 className="mt-3 text-lg font-semibold text-foreground">Email verified</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Your account is now active. You can sign in.
          </p>
        </motion.div>
        <Link
          href={`/p/${encodeURIComponent(slug)}/login`}
          className="mt-6 block text-center text-sm font-medium text-violet-600 hover:underline dark:text-violet-400"
        >
          Continue to sign in
        </Link>
      </AuthShell>
    );
  }

  if (token && state.kind === "verifying" && verifyM.isPending) {
    return (
      <AuthShell mode="minimal">
        <motion.p
          variants={fadeUp}
          initial="hidden"
          animate="show"
          className="text-sm text-muted-foreground"
        >
          Verifying your email…
        </motion.p>
      </AuthShell>
    );
  }

  // Token falló o no hay token → formulario de reenvío.
  return (
    <ResendForm slug={slug} presetEmail={presetEmail} verifyFailed={Boolean(token) && verifyM.isError} />
  );
}

function ResendForm({
  slug,
  presetEmail,
  verifyFailed,
}: {
  slug: string;
  presetEmail: string;
  verifyFailed: boolean;
}) {
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const resendM = useMutation({
    mutationFn: (email: string) => resendEndUserVerification(slug, email),
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
      await resendM.mutateAsync(email);
      setSent(true);
    } catch (err) {
      setError(
        err instanceof NexusApiError ? err.message : "Could not connect to the server.",
      );
    }
  }

  return (
    <AuthShell mode="minimal">
      <motion.header variants={fadeUp} initial="hidden" animate="show">
        <h1 className="text-2xl font-bold tracking-tight text-foreground">
          Verify your email
        </h1>
      </motion.header>

      {verifyFailed ? (
        <motion.div
          role="alert"
          initial={{ opacity: 0, y: -4 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
          className="mt-5 flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive"
        >
          <ShieldAlert size={16} className="shrink-0" />
          <span>The verification link is invalid or has expired.</span>
        </motion.div>
      ) : null}

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

      {sent ? (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
          className="mt-5 rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-4 text-sm text-emerald-700 dark:text-emerald-300"
        >
          If an account exists, a new verification link is on its way.
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
            defaultValue={presetEmail}
            placeholder="you@example.com"
            autoComplete="email"
            required
            disabled={resendM.isPending}
          />
        </div>
        <Button
          type="submit"
          className="h-12 w-full rounded-xl bg-violet-600 text-white hover:bg-violet-700 dark:bg-violet-500 dark:hover:bg-violet-600"
          disabled={resendM.isPending}
        >
          {resendM.isPending ? "Sending…" : "Resend verification link"}
        </Button>
      </form>
    </AuthShell>
  );
}
