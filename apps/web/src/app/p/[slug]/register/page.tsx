"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { FormEvent, Suspense, use, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { motion } from "motion/react";
import { CheckCircle2, Eye, EyeOff, ShieldAlert } from "lucide-react";
import { NexusApiError } from "@/lib/api/client";
import { registerEndUser } from "@/features/end-user/api";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AuthShell } from "@/app/login/AuthShell";

/**
 * Alta pública de usuario final por proyecto (sustituye al flujo Thymeleaf). Tras el alta,
 * el backend envía el email de verificación (estado PENDING_VERIFICATION) y se muestra la
 * confirmación. Si el registro público está deshabilitado, la API responde 404.
 */
export default function EndUserRegisterPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = use(params);
  return (
    <Suspense fallback={<AuthShell mode="register" />}>
      <EndUserRegisterScreen slug={slug} />
    </Suspense>
  );
}

function EndUserRegisterScreen({ slug }: { slug: string }) {
  const searchParams = useSearchParams();
  const continueUrl = searchParams.get("continue") ?? undefined;

  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);

  const registerM = useMutation({
    mutationFn: (input: {
      email: string;
      password: string;
      displayName?: string;
      username?: string;
    }) => registerEndUser(slug, input),
  });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    const formData = new FormData(event.currentTarget);
    const email = String(formData.get("email") ?? "").trim();
    const password = String(formData.get("password") ?? "");
    const displayName = String(formData.get("displayName") ?? "").trim();
    const username = String(formData.get("username") ?? "").trim();

    if (!email) {
      setError("Please enter your email address.");
      return;
    }
    if (password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }

    try {
      await registerM.mutateAsync({ email, password, displayName, username });
      setDone(email);
    } catch (err) {
      if (err instanceof NexusApiError) {
        if (err.status === 409 || err.code === "email_exists") {
          setError("An account with this email already exists. Try signing in.");
          return;
        }
        if (err.status === 404) {
          setError("Sign-up is not available for this project.");
          return;
        }
        if (err.status === 400) {
          setError(err.message);
          return;
        }
        setError(err.message);
      } else {
        setError("Could not connect to the server.");
      }
    }
  }

  if (done) {
    return (
      <AuthShell mode="register">
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
          className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-5"
        >
          <CheckCircle2 className="text-emerald-600 dark:text-emerald-400" />
          <h2 className="mt-3 text-lg font-semibold text-foreground">
            Check your email
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            We sent a verification link to{" "}
            <span className="font-medium text-foreground">{done}</span>. Click it to
            activate your account.
          </p>
        </motion.div>
        <Link
          href={{
            pathname: `/p/${encodeURIComponent(slug)}/login`,
            query: continueUrl ? { continue: continueUrl } : {},
          }}
          className="mt-6 block text-center text-sm font-medium text-violet-600 hover:underline dark:text-violet-400"
        >
          Back to sign in
        </Link>
      </AuthShell>
    );
  }

  return (
    <AuthShell mode="register">
      <motion.header variants={fadeUp} initial="hidden" animate="show">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">
          Create your account
        </h1>
        <p className="mt-1.5 text-sm text-muted-foreground">
          Sign up for{" "}
          <span className="font-medium text-foreground">{slug}</span>.
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
            disabled={registerM.isPending}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="displayName" className="text-sm font-medium">
            Display name <span className="text-muted-foreground">(optional)</span>
          </Label>
          <Input
            id="displayName"
            name="displayName"
            className="h-12 bg-white px-3 focus-visible:ring-violet-500/30 dark:bg-input/30"
            placeholder="Your name"
            autoComplete="name"
            disabled={registerM.isPending}
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
              placeholder="At least 8 characters"
              autoComplete="new-password"
              required
              minLength={8}
              disabled={registerM.isPending}
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

        <Button
          type="submit"
          className="h-12 w-full rounded-xl bg-violet-600 text-white hover:bg-violet-700 dark:bg-violet-500 dark:hover:bg-violet-600"
          disabled={registerM.isPending}
        >
          {registerM.isPending ? "Creating account…" : "Create account"}
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-muted-foreground">
        Already have an account?{" "}
        <Link
          href={{
            pathname: `/p/${encodeURIComponent(slug)}/login`,
            query: continueUrl ? { continue: continueUrl } : {},
          }}
          className="font-medium text-violet-600 hover:text-violet-700 hover:underline dark:text-violet-400 dark:hover:text-violet-300"
        >
          Sign in
        </Link>
      </p>
    </AuthShell>
  );
}
