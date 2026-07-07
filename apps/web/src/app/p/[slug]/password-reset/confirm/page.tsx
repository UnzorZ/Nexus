"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { FormEvent, Suspense, use, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { motion } from "motion/react";
import { CheckCircle2, Eye, EyeOff, ShieldAlert } from "lucide-react";
import { NexusApiError } from "@/lib/api/client";
import { confirmEndUserPasswordReset } from "@/features/end-user/api";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AuthShell } from "@/app/login/AuthShell";

/**
 * Confirmación de reseteo de contraseña (sustituye al flujo Thymeleaf). Lee el token del
 * query param (desde el email), pide la nueva contraseña y la envía al API. Al confirmar,
 * el backend revoca las sesiones y hace bump de authz_version (invalida tokens antiguos).
 */
export default function EndUserPasswordResetConfirmPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  return (
    <Suspense fallback={<AuthShell mode="minimal" />}>
      <PasswordResetConfirmScreen slugPromise={params} />
    </Suspense>
  );
}

function PasswordResetConfirmScreen({
  slugPromise,
}: {
  slugPromise: Promise<{ slug: string }>;
}) {
  const { slug } = use(slugPromise);
  const searchParams = useSearchParams();
  const token = searchParams.get("token") ?? "";

  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const confirmM = useMutation({
    mutationFn: ({ token, newPassword }: { token: string; newPassword: string }) =>
      confirmEndUserPasswordReset(slug, token, newPassword),
  });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const formData = new FormData(event.currentTarget);
    const newPassword = String(formData.get("newPassword") ?? "");
    const confirm = String(formData.get("confirm") ?? "");
    if (newPassword.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }
    if (newPassword !== confirm) {
      setError("Passwords do not match.");
      return;
    }
    try {
      await confirmM.mutateAsync({ token, newPassword });
      setDone(true);
    } catch (err) {
      if (err instanceof NexusApiError) {
        setError(err.message);
      } else {
        setError("Could not connect to the server.");
      }
    }
  }

  if (done) {
    return (
      <AuthShell mode="minimal">
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
          className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-5 text-center"
        >
          <CheckCircle2 className="mx-auto text-emerald-600 dark:text-emerald-400" />
          <h2 className="mt-3 text-lg font-semibold text-foreground">Password updated</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            You can now sign in with your new password.
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

  if (!token) {
    return (
      <AuthShell mode="minimal">
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
          className="flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive"
        >
          <ShieldAlert size={16} className="shrink-0" />
          <span>This reset link is invalid. Request a new one.</span>
        </motion.div>
      </AuthShell>
    );
  }

  return (
    <AuthShell mode="minimal">
      <motion.header variants={fadeUp} initial="hidden" animate="show">
        <h1 className="text-2xl font-bold tracking-tight text-foreground">
          Set a new password
        </h1>
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
          <Label htmlFor="newPassword" className="text-sm font-medium">
            New password
          </Label>
          <div className="relative">
            <Input
              id="newPassword"
              name="newPassword"
              type={showPassword ? "text" : "password"}
              className="h-12 bg-white px-3 pr-11 focus-visible:ring-violet-500/30 dark:bg-input/30"
              placeholder="At least 8 characters"
              autoComplete="new-password"
              required
              minLength={8}
              autoFocus
              disabled={confirmM.isPending}
            />
            <button
              type="button"
              className="absolute right-2 top-1/2 inline-flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
              aria-label={showPassword ? "Hide password" : "Show password"}
              aria-pressed={showPassword}
              tabIndex={-1}
              onClick={() => setShowPassword((v) => !v)}
            >
              {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="confirm" className="text-sm font-medium">
            Confirm password
          </Label>
          <Input
            id="confirm"
            name="confirm"
            type={showPassword ? "text" : "password"}
            className="h-12 bg-white px-3 focus-visible:ring-violet-500/30 dark:bg-input/30"
            placeholder="Re-enter the new password"
            autoComplete="new-password"
            required
            minLength={8}
            disabled={confirmM.isPending}
          />
        </div>

        <Button
          type="submit"
          className="h-12 w-full rounded-xl bg-violet-600 text-white hover:bg-violet-700 dark:bg-violet-500 dark:hover:bg-violet-600"
          disabled={confirmM.isPending}
        >
          {confirmM.isPending ? "Updating…" : "Update password"}
        </Button>
      </form>
    </AuthShell>
  );
}
