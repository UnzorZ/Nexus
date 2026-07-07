"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, use, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { motion } from "motion/react";
import { CheckCircle2, Eye, EyeOff, ShieldAlert } from "lucide-react";
import { NexusApiError } from "@/lib/api/client";
import {
  EndUserLoginResult,
  loginEndUser,
  verifyEndUserMfa,
} from "@/features/end-user/api";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AuthShell } from "@/app/login/AuthShell";

/**
 * Login de usuario final por proyecto (sustituye al flujo Thymeleaf). Tras un login JSON
 * correcto, si el backend devuelve `{ redirect }` (la URL absoluta del API de
 * `/oauth2/authorize` a reanudar) se hace una navegación de alto nivel al host del API
 * para que la cookie de sesión viaja y el Authorization Server reanude el flujo.
 */
export default function EndUserLoginPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = use(params);
  return (
    <Suspense fallback={<AuthShell mode="login" />}>
      <EndUserLoginScreen slug={slug} />
    </Suspense>
  );
}

function EndUserLoginScreen({ slug }: { slug: string }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const continueUrl = searchParams.get("continue") ?? undefined;

  const [error, setError] = useState<string | null>(null);
  const [emailNotVerified, setEmailNotVerified] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [step, setStep] = useState<"password" | "mfa">("password");

  const loginM = useMutation({
    mutationFn: (input: { email: string; password: string }) =>
      loginEndUser(slug, { ...input, continueUrl }),
  });
  const mfaM = useMutation({
    mutationFn: (code: string) => verifyEndUserMfa(slug, code, continueUrl),
  });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setEmailNotVerified(null);

    const formData = new FormData(event.currentTarget);
    const email = String(formData.get("email") ?? "").trim();
    const password = String(formData.get("password") ?? "");

    if (!email) {
      setError("Please enter your email address.");
      return;
    }
    if (!password) {
      setError("Please enter your password.");
      return;
    }

    try {
      const result: EndUserLoginResult = await loginM.mutateAsync({ email, password });
      if (result.code === "mfa_required") {
        // Contraseña válida pero MFA activa: pasar al paso del código TOTP.
        setError(null);
        setStep("mfa");
        return;
      }
      if (result.redirect) {
        // Top-level nav al host del API: la cookie de sesión viaja y el AS reanuda.
        window.location.href = result.redirect;
        return;
      }
      // Sin continue: aterrizamos en el portal del usuario final.
      router.push(`/p/${encodeURIComponent(slug)}/account`);
    } catch (err) {
      if (err instanceof NexusApiError && err.code === "email_not_verified") {
        setEmailNotVerified(email);
        return;
      }
      if (err instanceof NexusApiError) {
        setError(err.message);
      } else {
        setError("Could not connect to the server.");
      }
    }
  }

  async function handleMfaSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const formData = new FormData(event.currentTarget);
    const code = String(formData.get("code") ?? "").trim();
    if (!code) {
      setError("Enter your 6-digit code or a recovery code.");
      return;
    }
    try {
      const result: EndUserLoginResult = await mfaM.mutateAsync(code);
      if (result.redirect) {
        window.location.href = result.redirect;
        return;
      }
      router.push(`/p/${encodeURIComponent(slug)}/account`);
    } catch (err) {
      setError(err instanceof NexusApiError ? err.message : "Could not connect to the server.");
    }
  }

  return (
    <AuthShell mode="login">
      <motion.header variants={fadeUp} initial="hidden" animate="show">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">
          {step === "mfa" ? "Two-factor code" : "Sign in"}
        </h1>
        <p className="mt-1.5 text-sm text-muted-foreground">
          {step === "mfa"
            ? "Enter the 6-digit code from your authenticator app, or a recovery code."
            : <>Access your account for{" "}<span className="font-medium text-foreground">{slug}</span>.</>}
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

      {emailNotVerified ? (
        <motion.div
          role="status"
          initial={{ opacity: 0, y: -4 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
          className="mt-5 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-700 dark:text-amber-300"
        >
          <div className="flex items-center gap-2 font-medium">
            <CheckCircle2 size={16} className="shrink-0" />
            Verify your email
          </div>
          <p className="mt-1 text-amber-700/80 dark:text-amber-300/80">
            We sent a verification link to{" "}
            <span className="font-medium">{emailNotVerified}</span>. Confirm it to sign in.
          </p>
          <Link
            href={`/p/${encodeURIComponent(slug)}/verify-email?email=${encodeURIComponent(emailNotVerified)}`}
            className="mt-2 inline-block font-medium text-amber-700 underline-offset-2 hover:underline dark:text-amber-300"
          >
            Resend verification link
          </Link>
        </motion.div>
      ) : null}

      {step === "password" ? (
      <form className="mt-7 space-y-4" onSubmit={handleSubmit}>
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
            disabled={loginM.isPending}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="password" className="text-sm font-medium">
            Password
          </Label>
          <div className="relative">
            <Input
              id="password"
              name="password"
              type={showPassword ? "text" : "password"}
              className="h-12 bg-white px-3 pr-11 focus-visible:ring-violet-500/30 dark:bg-input/30"
              placeholder="Enter your password"
              autoComplete="current-password"
              required
              disabled={loginM.isPending}
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

        <div className="flex justify-end py-1">
          <Link
            href={`/p/${encodeURIComponent(slug)}/password-reset`}
            className="text-sm font-medium text-muted-foreground hover:text-violet-600 dark:hover:text-violet-400"
          >
            Forgot password?
          </Link>
        </div>

        <Button
          type="submit"
          className="h-12 w-full rounded-xl bg-violet-600 text-white hover:bg-violet-700 dark:bg-violet-500 dark:hover:bg-violet-600"
          disabled={loginM.isPending}
        >
          {loginM.isPending ? "Signing in…" : "Sign in"}
        </Button>
      </form>
      ) : (
      <form className="mt-7 space-y-4" onSubmit={handleMfaSubmit}>
        <div className="space-y-1.5">
          <Label htmlFor="code" className="text-sm font-medium">
            Authentication code
          </Label>
          <Input
            id="code"
            name="code"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            className="h-12 bg-white px-3 text-center text-lg tracking-[0.4em] focus-visible:ring-violet-500/30 dark:bg-input/30"
            placeholder="000000"
            autoFocus
            required
            disabled={mfaM.isPending}
          />
          <p className="text-xs text-muted-foreground">
            Enter the 6-digit code from your authenticator app, or paste a recovery code.
          </p>
        </div>

        <Button
          type="submit"
          className="h-12 w-full rounded-xl bg-violet-600 text-white hover:bg-violet-700 dark:bg-violet-500 dark:hover:bg-violet-600"
          disabled={mfaM.isPending}
        >
          {mfaM.isPending ? "Verifying…" : "Verify"}
        </Button>

        <button
          type="button"
          onClick={() => {
            setStep("password");
            setError(null);
          }}
          className="block w-full text-center text-sm font-medium text-muted-foreground hover:text-foreground"
        >
          ← Use a different account
        </button>
      </form>
      )}

      <p className="mt-6 text-center text-sm text-muted-foreground">
        New here?{" "}
        <Link
          href={`/p/${encodeURIComponent(slug)}/register`}
          className="font-medium text-violet-600 hover:text-violet-700 hover:underline dark:text-violet-400 dark:hover:text-violet-300"
        >
          Create an account
        </Link>
      </p>
    </AuthShell>
  );
}
