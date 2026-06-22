"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useState } from "react";
import { motion } from "motion/react";
import { Eye, EyeOff, ShieldAlert } from "lucide-react";
import { createNexusAccount } from "@/features/accounts/api";
import { NexusApiError } from "@/lib/api/client";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AuthShell } from "../login/AuthShell";

export default function RegisterPage() {
  return (
    <Suspense fallback={<AuthShell />}>
      <RegisterScreen />
    </Suspense>
  );
}

function RegisterScreen() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const continuePath = searchParams.get("continue") ?? "/projects";

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
    const displayName = String(formData.get("displayName") ?? "").trim();

    if (!email || !password || !displayName) {
      setError("Please fill in all fields.");
      setIsPending(false);
      return;
    }
    if (password.length < 8) {
      setError("Password must be at least 8 characters.");
      setIsPending(false);
      return;
    }

    try {
      await createNexusAccount({ email, password, displayName });
      router.push(`/login?continue=${encodeURIComponent(continuePath)}`);
    } catch (submitError) {
      if (submitError instanceof NexusApiError) {
        setError(submitError.message);
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
          Create your account
        </h1>
        <p className="mt-1.5 text-sm text-muted-foreground">
          Register a Nexus account to access the panel.
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
          <Label htmlFor="displayName" className="text-sm font-medium">
            Display name
          </Label>
          <Input
            id="displayName"
            name="displayName"
            type="text"
            className="h-11 px-3"
            placeholder="Marcos"
            autoComplete="name"
            required
            disabled={isPending}
          />
        </div>

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
              placeholder="Min. 8 characters"
              autoComplete="new-password"
              minLength={8}
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
          {isPending ? "Creating account…" : "Create account"}
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-muted-foreground">
        Already have an account?{" "}
        <Link
          href={{
            pathname: "/login",
            query: continuePath ? { continue: continuePath } : {},
          }}
          className="font-medium text-primary hover:underline"
        >
          Sign in
        </Link>
      </p>
    </AuthShell>
  );
}
