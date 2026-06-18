"use client";

import { motion } from "motion/react";
import { Code2, Layers, ShieldCheck } from "lucide-react";
import { SPRING_SNAPPY } from "@/components/dashboard/anim";

const FEATURES = [
  { Icon: ShieldCheck, label: "Project isolation" },
  { Icon: Layers, label: "Reusable modules" },
  { Icon: Code2, label: "SDK integration" },
];

/**
 * Split-screen auth shell: a dotted-grid "product panel" on the left and the
 * authentication form slot on the right. Ports the panel login brand and shares
 * it between the login and register pages.
 */
export function AuthShell({ children }: { children?: React.ReactNode }) {
  return (
    <div className="auth-layout">
      {/* Product panel */}
      <section className="auth-product" aria-hidden>
        <div className="auth-product__inner">
          <div className="auth-wordmark">
            <span className="auth-wordmark__dot" />
            NEXUS
          </div>

          <div className="auth-copy">
            <h1 className="auth-copy__title">
              Your projects. One control plane.
            </h1>
            <p className="auth-copy__subtitle">
              Manage shared identity, permissions, API keys and reusable modules
              from one place.
            </p>

            <ul className="auth-feature-list">
              {FEATURES.map(({ Icon, label }) => (
                <li key={label}>
                  <Icon />
                  <span>{label}</span>
                </li>
              ))}
            </ul>
          </div>

          <footer className="auth-product__footer">
            <strong>Nexus Control Plane</strong>
            <span>Self-hosted</span>
          </footer>
        </div>
      </section>

      {/* Authentication panel */}
      <section className="auth-panel">
        <motion.div
          className="auth-panel__inner"
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
        >
          <div className="auth-wordmark auth-wordmark--mobile">
            <span className="auth-wordmark__dot" />
            NEXUS
          </div>
          {children}
        </motion.div>

        <footer className="auth-footer">
          <span>Nexus Control Plane</span>
          <span aria-hidden>·</span>
          <span>v0.1</span>
        </footer>
      </section>
    </div>
  );
}
