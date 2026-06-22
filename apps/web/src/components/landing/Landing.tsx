"use client";

import "@/app/landing.css";

import Link from "next/link";
import { motion } from "motion/react";
import { ArrowRight, Code2, Server, ShieldCheck } from "lucide-react";
import {
  fadeUp,
  staggerContainer,
} from "@/components/dashboard/anim";
import { LandingAurora } from "./LandingAurora";
import { LandingConstellation } from "./LandingConstellation";
import { LandingSections } from "./LandingSections";
import { Logo } from "./Logo";

const META = [
  { Icon: ShieldCheck, label: "Source of truth" },
  { Icon: Server, label: "Self-hosted" },
  { Icon: Code2, label: "Open-source ready" },
];

export function Landing() {
  return (
    <div className="landing-root">
      <LandingAurora />
      <div className="landing-grain" aria-hidden="true" />

      <div className="landing-content">
        <nav className="landing-nav" aria-label="Primary">
          <div className="landing-wrap landing-nav__inner">
            <Logo height={26} />
            <div className="landing-nav__links">
              <Link href="/login" className="landing-nav__link landing-nav__link--ghost-text">
                Sign in
              </Link>
              <Link href="/register" className="nx-btn nx-btn--ghost" style={{ height: 40, padding: "0 1rem", fontSize: "0.86rem" }}>
                Create account
              </Link>
            </div>
          </div>
        </nav>

        <main>
          {/* Hero */}
          <section className="landing-hero">
            <LandingConstellation />
            <div className="landing-hero__scrim" />

            <motion.div
              className="landing-wrap landing-hero__copy"
              variants={staggerContainer}
              initial="hidden"
              animate="show"
            >
              <motion.p variants={fadeUp} className="nx-kicker">
                <span className="nx-kicker__dot" />
                Nexus · Control plane
                <span className="nx-kicker__rule" />
              </motion.p>

              <motion.h1 variants={fadeUp} className="nx-h1 landing-hero__title">
                One control plane for{" "}
                <span className="nx-grad">every app</span> you ship.
              </motion.h1>

              <motion.p variants={fadeUp} className="nx-lead landing-hero__sub">
                Nexus handles the things every app needs — signing people in,
                deciding who can do what, holding the keys, and watching
                everything stays alive — so each project can focus on what makes
                it different.
              </motion.p>

              <motion.div variants={fadeUp} className="landing-hero__cta">
                <Link href="/login" className="nx-btn nx-btn--primary">
                  Open the console
                  <ArrowRight />
                </Link>
                <Link href="/register" className="nx-btn nx-btn--ghost">
                  Create your account
                </Link>
              </motion.div>

              <motion.div variants={fadeUp} className="landing-hero__meta">
                {META.map((m) => (
                  <span key={m.label} className="landing-hero__meta-item">
                    <m.Icon />
                    {m.label}
                  </span>
                ))}
              </motion.div>
            </motion.div>
          </section>

          <LandingSections />
        </main>
      </div>
    </div>
  );
}
