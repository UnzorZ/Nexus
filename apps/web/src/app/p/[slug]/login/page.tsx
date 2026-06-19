"use client";

import { use } from "react";
import Link from "next/link";
import { motion } from "motion/react";
import { Check, ShieldCheck } from "lucide-react";
import { AuthShell } from "@/app/login/AuthShell";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import "@/app/login/auth.css";

const SCOPES = [
  "View your account profile and email",
  "Access project resources on your behalf",
  "Maintain session and webhook integrity",
];

export default function OAuthLoginPage({
  params,
}: {
  params: Promise<{ slug: string }>;
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { slug } = use(params);

  return (
    <AuthShell>
      <motion.header
        className="auth-header"
        variants={fadeUp}
        initial="hidden"
        animate="show"
      >
        <h1 className="auth-header__title">Authorize request</h1>
        <p className="auth-header__subtitle">
          An application wants to access the{" "}
          <strong style={{ color: "var(--auth-ink)" }}>{slug}</strong> project on
          your behalf.
        </p>
      </motion.header>

      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={SPRING_SNAPPY}
        className="auth-card"
      >
        <h2 className="auth-card__title">This application will be able to:</h2>
        <ul className="auth-perm-list">
          {SCOPES.map((scope) => (
            <li key={scope}>
              <Check />
              <span>{scope}</span>
            </li>
          ))}
        </ul>
      </motion.div>

      <p className="auth-trust-note" style={{ marginTop: 18 }}>
        This does not give the application your Nexus password or session
        credentials. You can revoke access at any time from your project settings.
      </p>

      <form className="auth-form" style={{ marginTop: 22 }}>
        <div className="auth-button-row">
          <Link href="/login" className="auth-submit--outline">
            Cancel
          </Link>
          <button type="submit" className="auth-submit" disabled>
            Authorize
          </button>
        </div>
      </form>

      <div className="auth-session-note" aria-hidden>
        <ShieldCheck />
        <span>
          Signing in to <strong>{slug}</strong> via Nexus identity.
        </span>
      </div>
    </AuthShell>
  );
}
