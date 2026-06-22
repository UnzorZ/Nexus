"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useState } from "react";
import { motion } from "motion/react";
import { CheckCircle2, Eye, EyeOff, Lock, ShieldAlert } from "lucide-react";
import { apiClient, NexusApiError } from "@/lib/api/client";
import { CSRF_HEADER_NAME, ensureCsrfToken } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AuthShell } from "./AuthShell";

export default function LoginPage() {
  return (
    <Suspense fallback={<AuthShell />}>
      <LoginScreen />
    </Suspense>
  );
}

function LoginScreen() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const continuePath = searchParams.get("continue") ?? "/projects";
  const loggedOut = searchParams.get("logout") === "1";

  const [error, setError] = useState<string | null>(null);
  const [isPending, setIsPending] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsPending(true);

    const formData = new FormData(event.currentTarget);
    const email = String(formData.get("email") ?? "").trim();
    const password = String(formData.get("password") ?? "");

    if (!email) {
      setError("Please enter your email address.");
      setIsPending(false);
      return;
    }
    if (!password) {
      setError("Please enter your password.");
      setIsPending(false);
      return;
    }

    try {
      const csrfToken = await ensureCsrfToken();

      await apiClient.post<unknown>(
        apiRoutes.panel.session.loginJson,
        { email, password },
        {
          headers: { [CSRF_HEADER_NAME]: csrfToken },
          redirect: "manual",
          errorMessage: "Incorrect email address or password.",
        },
      );

      router.push(continuePath);
      router.refresh();
    } catch (err) {
      if (err instanceof NexusApiError) {
        setError(err.message);
      } else {
        setError("Could not connect to the Nexus API.");
      }
    } finally {
      setIsPending(false);
    }
  }

  return (
    <AuthShell>
      <motion.header variants={fadeUp} initial="hidden" animate="show">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
          Welcome back
        </h1>
        <p className="mt-1.5 text-sm text-muted-foreground">
          Sign in to your Nexus account.
        </p>
      </motion.header>

      {loggedOut ? (
        <motion.div
          role="status"
          initial={{ opacity: 0, y: -4 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
          className="mt-5 flex items-center gap-2 rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-sm text-emerald-700 dark:text-emerald-300"
        >
          <CheckCircle2 size={16} className="shrink-0" />
          <span>You have been signed out.</span>
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

      <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
        <div className="space-y-1.5">
          <Label htmlFor="email" className="text-sm font-medium">
            Email address
          </Label>
          <Input
            id="email"
            name="email"
            type="email"
            className="h-11 px-3"
            placeholder="you@example.com"
            autoComplete="email"
            inputMode="email"
            autoFocus
            required
            disabled={isPending}
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
              className="h-11 px-3 pr-11"
              placeholder="Enter your password"
              autoComplete="current-password"
              required
              disabled={isPending}
            />
            <button
              type="button"
              className="absolute right-2 top-1/2 inline-flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
              aria-label={showPassword ? "Hide password" : "Show password"}
              aria-pressed={showPassword}
              aria-controls="password"
              tabIndex={-1}
              onClick={() => setShowPassword((v) => !v)}
            >
              {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
        </div>

        <Button type="submit" className="h-11 w-full" disabled={isPending}>
          {isPending ? "Signing in…" : "Sign in"}
        </Button>
      </form>

      <div className="mt-5 flex items-center justify-center gap-2 text-xs text-muted-foreground">
        <Lock size={14} className="shrink-0" />
        <span>Your session is protected and can be revoked at any time.</span>
      </div>

      <p className="mt-6 text-center text-sm text-muted-foreground">
        Don&apos;t have an account?{" "}
        <Link
          href={{
            pathname: "/register",
            query: continuePath ? { continue: continuePath } : {},
          }}
          className="font-medium text-primary hover:underline"
        >
          Create one
        </Link>
      </p>
    </AuthShell>
  );
}
