"use client";

import "./auth.css";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useState } from "react";
import { motion } from "motion/react";
import {
  CheckCircle2,
  Eye,
  EyeOff,
  Lock,
  ShieldAlert,
} from "lucide-react";
import { apiClient, NexusApiError } from "@/lib/api/client";
import { CSRF_HEADER_NAME, ensureCsrfToken } from "@/lib/api/csrf";
import { apiRoutes } from "@/lib/api/routes";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
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
      <motion.header
        className="auth-header"
        variants={fadeUp}
        initial="hidden"
        animate="visible"
      >
        <h1 className="auth-header__title">Welcome back</h1>
        <p className="auth-header__subtitle">Sign in to your Nexus account.</p>
      </motion.header>

      {loggedOut ? (
        <motion.div
          className="auth-alert auth-alert--success"
          role="status"
          initial={{ opacity: 0, y: -4 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
        >
          <CheckCircle2 />
          <span>You have been signed out.</span>
        </motion.div>
      ) : null}

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
              placeholder="Enter your password"
              autoComplete="current-password"
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
          {isPending ? "Signing in…" : "Sign in"}
        </button>
      </form>

      <div className="auth-session-note">
        <Lock />
        <span>Your session is protected and can be revoked at any time.</span>
      </div>

      <p className="auth-switch">
        Don&apos;t have an account?{" "}
        <Link
          href={{
            pathname: "/register",
            query: continuePath ? { continue: continuePath } : {},
          }}
        >
          Create one
        </Link>
      </p>
    </AuthShell>
  );
}
