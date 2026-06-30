"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { motion } from "motion/react";
import { CheckCircle2, Eye, EyeOff, ShieldAlert } from "lucide-react";
import { apiClient, NexusApiError } from "@/lib/api/client";
import { CSRF_HEADER_NAME, ensureCsrfToken } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";
import { isInternalPath } from "@/lib/auth/continue-url";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AuthShell } from "./AuthShell";

export default function LoginPage() {
  return (
    <Suspense fallback={<AuthShell mode="login" />}>
      <LoginScreen />
    </Suspense>
  );
}

function GoogleIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
      />
      <path
        fill="#34A853"
        d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
      />
      <path
        fill="#FBBC05"
        d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
      />
      <path
        fill="#EA4335"
        d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
      />
    </svg>
  );
}

function XIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" fill="currentColor">
      <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" />
    </svg>
  );
}

function SocialButton({
  icon: Icon,
  label,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
}) {
  return (
    <button
      type="button"
      disabled
      title="Coming soon"
      className="inline-flex h-11 w-full cursor-not-allowed items-center justify-center gap-2.5 rounded-xl border border-input bg-background px-4 text-sm font-semibold text-foreground transition-colors hover:border-violet-500 hover:text-violet-600 dark:bg-input/30 dark:hover:text-violet-400"
    >
      <Icon className="size-4 shrink-0" />
      <span>{label}</span>
    </button>
  );
}

function OrDivider() {
  return (
    <div className="relative flex items-center py-2">
      <div className="flex-1 border-t border-border" />
      <span className="px-3 text-xs text-muted-foreground">or</span>
      <div className="flex-1 border-t border-border" />
    </div>
  );
}

function LoginScreen() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const rawContinue = searchParams.get("continue");
  const continuePath =
    rawContinue && isInternalPath(rawContinue) ? rawContinue : "/projects";
  const loggedOut = searchParams.get("logout") === "1";

  const [error, setError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);

  const loginM = useMutation({
    mutationFn: async ({
      email,
      password,
    }: {
      email: string;
      password: string;
    }) => {
      const csrfToken = await ensureCsrfToken();
      return apiClient.post<unknown>(
        apiRoutes.panel.session.loginJson,
        { email, password },
        {
          headers: { [CSRF_HEADER_NAME]: csrfToken },
          redirect: "manual",
          errorMessage: "Incorrect email address or password.",
        },
      );
    },
  });
  const isPending = loginM.isPending;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

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
      await loginM.mutateAsync({ email, password });
      router.push(continuePath);
      router.refresh();
    } catch (err) {
      if (err instanceof NexusApiError && err.code === "account_suspended") {
        // El interceptor global ya está redirigiendo a /login/suspended; no
        // mostramos el error en línea para evitar el parpadeo.
        return;
      }
      if (err instanceof NexusApiError) {
        setError(err.message);
      } else {
        setError("Could not connect to the Nexus API.");
      }
    }
  }

  return (
    <AuthShell mode="login">
      <motion.header variants={fadeUp} initial="hidden" animate="show">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">
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

      <div className="mt-7 space-y-3">
        <SocialButton icon={GoogleIcon} label="Sign in with Google" />
        <SocialButton icon={XIcon} label="Sign in with X" />
      </div>

      <OrDivider />

      <form className="space-y-4" onSubmit={handleSubmit}>
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
              className="h-12 bg-white px-3 pr-11 focus-visible:ring-violet-500/30 dark:bg-input/30"
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

        <div className="flex justify-end py-1">
          <Link
            href="#"
            className="text-sm font-medium text-muted-foreground hover:text-violet-600 dark:hover:text-violet-400"
          >
            Forgot password?
          </Link>
        </div>

        <Button
          type="submit"
          className="h-12 w-full rounded-xl bg-violet-600 text-white hover:bg-violet-700 dark:bg-violet-500 dark:hover:bg-violet-600"
          disabled={isPending}
        >
          {isPending ? "Signing in…" : "Sign in"}
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-muted-foreground">
        New to Nexus?{" "}
        <Link
          href={{
            pathname: "/register",
            query: continuePath ? { continue: continuePath } : {},
          }}
          className="font-medium text-violet-600 hover:text-violet-700 hover:underline dark:text-violet-400 dark:hover:text-violet-300"
        >
          Create an account
        </Link>
      </p>
    </AuthShell>
  );
}
