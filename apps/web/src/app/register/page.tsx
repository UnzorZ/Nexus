"use client";

import "../login/auth.css";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useState } from "react";
import { motion } from "motion/react";
import { Eye, EyeOff, ShieldAlert } from "lucide-react";
import { createNexusAccount } from "@/features/accounts/api";
import { NexusApiError } from "@/lib/api/client";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
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
      <motion.header
        className="auth-header"
        variants={fadeUp}
        initial="hidden"
        animate="visible"
      >
        <h1 className="auth-header__title">Create your account</h1>
        <p className="auth-header__subtitle">
          Register a Nexus account to access the panel.
        </p>
      </motion.header>

      {error ? (
        <motion.div
          className="auth-alert auth-alert--error"
          role="alert"
          initial={{ opacity: 0, y: -4 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
        >
          <ShieldAlert />
          <span>{error}</span>
        </motion.div>
      ) : null}

      <form className="auth-form" onSubmit={handleSubmit}>
        <div className="auth-field">
          <label className="auth-field__label" htmlFor="displayName">
            Display name
          </label>
          <input
            id="displayName"
            name="displayName"
            type="text"
            className="auth-field__input"
            placeholder="Marcos"
            autoComplete="name"
            required
            disabled={isPending}
          />
        </div>

        <div className="auth-field">
          <label className="auth-field__label" htmlFor="email">
            Email address
          </label>
          <input
            id="email"
            name="email"
            type="email"
            className="auth-field__input"
            placeholder="you@example.com"
            autoComplete="email"
            inputMode="email"
            autoFocus
            required
            disabled={isPending}
          />
        </div>

        <div className="auth-field">
          <label className="auth-field__label" htmlFor="password">
            Password
          </label>
          <div className="auth-password">
            <input
              id="password"
              name="password"
              type={showPassword ? "text" : "password"}
              className="auth-field__input"
              placeholder="Min. 8 characters"
              autoComplete="new-password"
              minLength={8}
              required
              disabled={isPending}
            />
            <button
              type="button"
              className="auth-eye"
              aria-label={showPassword ? "Hide password" : "Show password"}
              aria-pressed={showPassword}
              aria-controls="password"
              onClick={() => setShowPassword((v) => !v)}
              tabIndex={-1}
            >
              {showPassword ? <EyeOff /> : <Eye />}
            </button>
          </div>
        </div>

        <button type="submit" className="auth-submit" disabled={isPending}>
          {isPending ? "Creating account…" : "Create account"}
        </button>
      </form>

      <p className="auth-switch">
        Already have an account?{" "}
        <Link
          href={{
            pathname: "/login",
            query: continuePath ? { continue: continuePath } : {},
          }}
        >
          Sign in
        </Link>
      </p>
    </AuthShell>
  );
}
