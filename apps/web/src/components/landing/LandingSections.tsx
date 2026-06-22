"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { motion } from "motion/react";
import { ArrowRight } from "lucide-react";
import { EASE_OUT } from "@/components/dashboard/anim";
import { Logo } from "./Logo";

type RevealProps = {
  children: ReactNode;
  delay?: number;
  className?: string;
  as?: "div" | "li";
  tabIndex?: number;
};

/* Scroll-triggered reveal. Use as="li" inside lists so ul/ol keep valid markup. */
function Reveal({
  children,
  delay = 0,
  className,
  as = "div",
  tabIndex,
}: RevealProps) {
  const motionProps = {
    className,
    initial: { opacity: 0, y: 22 },
    whileInView: { opacity: 1, y: 0 },
    viewport: { once: true, margin: "-80px" },
    transition: { duration: 0.65, ease: EASE_OUT, delay },
    ...(tabIndex !== undefined ? { tabIndex } : {}),
  };

  if (as === "li") {
    return <motion.li {...motionProps}>{children}</motion.li>;
  }

  return <motion.div {...motionProps}>{children}</motion.div>;
}

/* ------------------------------------------------------------------ */
/*  001 — The idea                                                    */
/* ------------------------------------------------------------------ */

export function ManifestoSection() {
  return (
    <section className="nx-manifesto">
      <div className="landing-wrap landing-wrap--narrow">
        <Reveal>
          <p className="nx-stamp">001 — The idea</p>
          <h2 className="nx-statement">
            Your apps declare what they can do.{" "}
            <span className="nx-grad">Nexus decides who can do it.</span>
          </h2>
          <p className="nx-body nx-statement-sub">
            Every app rebuilds the same foundations — sign-in, permissions,
            keys, the quiet watch over whether it&apos;s still alive. Nexus is
            that foundation, built once and shared across everything you ship.
          </p>
        </Reveal>
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------ */
/*  002 — What Nexus does (typographic index)                         */
/* ------------------------------------------------------------------ */

const CAPS = [
  {
    n: "01",
    title: "Identity",
    desc: "Sign people into your apps — each with its own isolated accounts and login.",
    tag: "Sign-in",
  },
  {
    n: "02",
    title: "Permissions",
    desc: "Decide who can do what, from one shared catalog of roles and rules.",
    tag: "Access control",
  },
  {
    n: "03",
    title: "API keys",
    desc: "Hand each app its own keys. Rotate, scope, and revoke them anytime.",
    tag: "Credentials",
  },
  {
    n: "04",
    title: "Registry",
    desc: "Know your apps are alive. Heartbeats flag the moment one goes quiet.",
    tag: "Health",
  },
  {
    n: "05",
    title: "Audit",
    desc: "A clear, append-only record of everything sensitive that ever happens.",
    tag: "Always on",
  },
  {
    n: "06",
    title: "Notify",
    desc: "Reach your users — email and notifications from one shared service.",
    tag: "Messaging",
  },
];

const ROADMAP = [
  "Storage",
  "Vault",
  "Config",
  "Metrics",
  "Backups",
  "Documents",
];

export function CapabilitiesSection() {
  return (
    <section className="landing-section">
      <div className="landing-wrap">
        <Reveal className="nx-index-head">
          <p className="nx-stamp">002 — What Nexus does</p>
          <h2 className="nx-h2">
            One shared surface for the work every app repeats.
          </h2>
        </Reveal>

        <ul className="nx-index">
          {CAPS.map((c, i) => (
            <Reveal
              key={c.n}
              as="li"
              delay={i * 0.05}
              className="nx-index__row"
              tabIndex={0}
            >
              <span className="nx-index__num">{c.n}</span>
              <div className="nx-index__main">
                <h3 className="nx-index__name">{c.title}</h3>
                <p className="nx-index__desc">{c.desc}</p>
              </div>
              <span className="nx-index__tag">{c.tag}</span>
            </Reveal>
          ))}
        </ul>

        <Reveal delay={0.1}>
          <p className="nx-index-foot">
            On the roadmap —{" "}
            <span>{ROADMAP.join(" · ")}</span>
          </p>
        </Reveal>
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------ */
/*  003 — How it works (connected node flow)                          */
/* ------------------------------------------------------------------ */

const FLOW = [
  { label: "Declare", text: "Your app tells Nexus what it can do." },
  { label: "Decide", text: "You choose who's allowed, in one place." },
  { label: "Answer", text: "Your app asks. Nexus says yes or no." },
];

export function HowItWorksSection() {
  return (
    <section className="landing-section">
      <div className="landing-wrap landing-wrap--narrow nx-centered">
        <Reveal>
          <p className="nx-stamp">003 — How it works</p>
          <h2 className="nx-h2 nx-flow-title">Declare. Decide. Answer.</h2>
        </Reveal>
        <Reveal delay={0.1}>
          <div className="nx-flow">
            {FLOW.map((f) => (
              <div className="nx-flow__node" key={f.label}>
                <span className="nx-flow__dot" />
                <p className="nx-flow__label">{f.label}</p>
                <p className="nx-flow__text">{f.text}</p>
              </div>
            ))}
          </div>
        </Reveal>
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------ */
/*  Close — logo + CTA + self-hosted                                  */
/* ------------------------------------------------------------------ */

export function CloseSection() {
  return (
    <section className="nx-close">
      <div className="landing-wrap landing-wrap--narrow nx-centered">
        <Reveal>
          <div className="nx-close__logo">
            <Logo withText={false} height={56} asLink={false} />
          </div>
          <h2 className="nx-h2">
            Self-hosted. Yours.
            <br />
            The source of truth for everything you build.
          </h2>
          <p className="nx-lead nx-close__sub">
            Bring your apps to one center of gravity — claim your instance and
            connect your first one in minutes.
          </p>
          <div className="landing-foot__cta">
            <Link href="/login" className="nx-btn nx-btn--primary">
              Open the console
              <ArrowRight />
            </Link>
            <Link href="/register" className="nx-btn nx-btn--ghost">
              Create your account
            </Link>
          </div>
          <p className="nx-close__meta">
            Docker or a single host · No Kubernetes · Built to be open-sourced
          </p>
        </Reveal>

        <div className="landing-foot__bar">
          <Logo height={24} />
          <span>v0.1 · Self-hosted control plane</span>
          <span className="landing-foot__links">
            <Link href="/login">Sign in</Link>
            <Link href="/register">Create account</Link>
          </span>
        </div>
      </div>
    </section>
  );
}

export function LandingSections() {
  return (
    <>
      <ManifestoSection />
      <CapabilitiesSection />
      <HowItWorksSection />
      <CloseSection />
    </>
  );
}
