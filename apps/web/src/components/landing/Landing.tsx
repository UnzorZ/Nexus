"use client";

import { Fragment, type ReactNode } from "react";
import Link from "next/link";
import { motion } from "motion/react";
import {
  Activity,
  ArrowRight,
  CheckCircle2,
  Code2,
  Database,
  Gauge,
  HardDrive,
  History,
  KeyRound,
  Layers,
  Lock,
  Send,
  Server,
  Settings2,
  ShieldAlert,
  ShieldCheck,
  UserCog,
  Users,
} from "lucide-react";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ThemeToggle, ThemedLogo } from "@/components/ui/theme-toggle";
import { cn } from "@/lib/utils";
import {
  EASE_OUT,
  MotionCard,
  Stagger,
  fadeUp,
  tint,
} from "@/components/dashboard/anim";

type TintKey = keyof typeof tint;

/* ---- motion + layout helpers ---- */

function Reveal({
  children,
  delay = 0,
  className,
}: {
  children: ReactNode;
  delay?: number;
  className?: string;
}) {
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: 18 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-70px" }}
      transition={{ duration: 0.6, ease: EASE_OUT, delay }}
    >
      {children}
    </motion.div>
  );
}

function SectionHead({
  crumb,
  title,
  desc,
}: {
  crumb: string;
  title: string;
  desc?: string;
}) {
  return (
    <div className="mb-8 max-w-2xl">
      <div className="mb-2 text-xs text-muted-foreground">{crumb}</div>
      <h2 className="text-balance text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
        {title}
      </h2>
      {desc ? (
        <p className="mt-2 text-pretty text-muted-foreground">{desc}</p>
      ) : null}
    </div>
  );
}

/* ---- data (dashboard vocabulary: tinted chips, metrics, live dots) ---- */

const PREV_STEPS = [
  { Icon: CheckCircle2, label: "Project created" },
  { Icon: KeyRound, label: "API key issued" },
  { Icon: Activity, label: "SDK connected" },
];

const PREV_TILES: { Icon: typeof Users; title: string; c: TintKey }[] = [
  { Icon: Users, title: "Identity", c: "indigo" },
  { Icon: ShieldCheck, title: "Permissions", c: "violet" },
  { Icon: Activity, title: "Registry", c: "cyan" },
  { Icon: History, title: "Audit", c: "amber" },
];

const PRINCIPLES: {
  Icon: typeof ShieldCheck;
  title: string;
  desc: string;
  c: TintKey;
}[] = [
  {
    Icon: ShieldCheck,
    title: "Source of truth",
    desc: "One place holds the answer. When Nexus can’t be sure, nothing happens.",
    c: "violet",
  },
  {
    Icon: Layers,
    title: "Declare, don’t decide",
    desc: "Apps say what they can do. Nexus decides who may do it.",
    c: "indigo",
  },
  {
    Icon: Lock,
    title: "Fail-closed",
    desc: "In doubt, Nexus says no. Safety over convenience, always.",
    c: "cyan",
  },
];

// The full module catalog — every module Nexus is built around, whether it's
// available today or on the roadmap. Mirrors the dashboard's MODULE_CONFIGS.
type ModuleStatus = "available" | "planned";

const MODULES: {
  key: string;
  Icon: typeof Users;
  title: string;
  desc: string;
  c: TintKey;
  status: ModuleStatus;
}[] = [
  { key: "nexus/identity", Icon: Users, title: "Identity", desc: "Project-isolated users and OAuth/OIDC login.", c: "indigo", status: "available" },
  { key: "nexus/permissions", Icon: ShieldCheck, title: "Permissions", desc: "Catalog, roles, and assignments.", c: "violet", status: "available" },
  { key: "nexus/registry", Icon: Activity, title: "Registry", desc: "Instances and heartbeat health.", c: "cyan", status: "available" },
  { key: "nexus/notify", Icon: Send, title: "Notify", desc: "Email and notifications, one service.", c: "emerald", status: "available" },
  { key: "nexus/audit", Icon: History, title: "Audit", desc: "Append-only sensitive events.", c: "amber", status: "available" },
  { key: "nexus/storage", Icon: HardDrive, title: "Storage", desc: "Files and objects, per project.", c: "blue", status: "planned" },
  { key: "nexus/vault", Icon: Lock, title: "Vault", desc: "Secrets and encrypted values.", c: "violet", status: "planned" },
  { key: "nexus/config", Icon: Settings2, title: "Config", desc: "Dynamic configuration per project.", c: "indigo", status: "planned" },
  { key: "nexus/metrics", Icon: Gauge, title: "Metrics", desc: "Usage and uptime signals.", c: "cyan", status: "planned" },
  { key: "nexus/backup", Icon: Database, title: "Backups", desc: "Snapshots and restore.", c: "blue", status: "planned" },
];

const STEPS: { Icon: typeof CheckCircle2; label: string; text: string }[] = [
  { Icon: Layers, label: "Declare", text: "Your app publishes what it can do." },
  { Icon: UserCog, label: "Decide", text: "You grant those actions to roles and people." },
  { Icon: ShieldCheck, label: "Answer", text: "Your app asks Nexus. It answers yes or no." },
];

const SELF: { Icon: typeof Server; title: string; desc: string; c: TintKey }[] = [
  { Icon: Server, title: "Runs on your own server", desc: "Docker or a single host — no Kubernetes, no lock-in.", c: "cyan" },
  { Icon: ShieldAlert, title: "You hold the keys", desc: "Fail-closed by design. Your data, your rules.", c: "violet" },
  { Icon: Code2, title: "Built to be open-sourced", desc: "A clean modular core with nothing hidden.", c: "indigo" },
];

export function Landing() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      {/* Nav — clean header, dashboard-topbar register */}
      <header className="sticky top-0 z-40 border-b border-border bg-background/80 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
          <ThemedLogo height={26} />
          <div className="flex items-center gap-1">
            <ThemeToggle />
            <Button asChild variant="ghost" className="h-9 px-3 text-sm">
              <Link href="/login">Sign in</Link>
            </Button>
            <Button asChild className="h-9 px-4 text-sm">
              <Link href="/login">Open console</Link>
            </Button>
          </div>
        </div>
      </header>

      <Stagger root className="mx-auto max-w-6xl flex-1 px-6">
        {/* Hero */}
        <section className="grid items-center gap-10 py-16 lg:grid-cols-[1.05fr_0.95fr] lg:py-24">
          <motion.div variants={fadeUp}>
            <Badge
              variant="outline"
              className="mb-5 gap-1.5 bg-emerald-500/10 px-2.5 py-1 text-emerald-700 dark:text-emerald-300"
            >
              <span className="nexus-live relative h-1.5 w-1.5 rounded-full bg-emerald-500 text-emerald-500" />
              Control plane · v0.1
            </Badge>
            <h1 className="text-balance text-4xl font-semibold tracking-tight text-foreground sm:text-6xl">
              One control plane for every app you ship.
            </h1>
            <p className="mt-5 max-w-xl text-pretty text-lg leading-relaxed text-muted-foreground">
              Nexus handles the things every app needs — signing people in,
              deciding who can do what, holding the keys, and watching everything
              stays alive — so each project can focus on what makes it different.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Button asChild className="h-11 px-5 text-sm">
                <Link href="/login">
                  Open the console
                  <ArrowRight />
                </Link>
              </Button>
              <Button asChild variant="outline" className="h-11 px-5 text-sm">
                <Link href="/register">Create your account</Link>
              </Button>
            </div>
            <p className="mt-6 text-sm text-muted-foreground">
              Self-hosted · Fail-closed by design · Open-source ready
            </p>
          </motion.div>

          {/* Console preview — a real dashboard snippet */}
          <motion.div variants={fadeUp}>
            <Card className="overflow-hidden shadow-xl shadow-foreground/5">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle className="font-heading text-base">Unknown project</CardTitle>
                  <Badge className="gap-1.5 bg-emerald-500/15 text-emerald-700 hover:bg-emerald-500/15 dark:text-emerald-300">
                    <span className="nexus-live relative h-1.5 w-1.5 rounded-full bg-emerald-500 text-emerald-500" />
                    Active
                  </Badge>
                </div>
                <CardDescription>Control plane overview</CardDescription>
              </CardHeader>
              <CardContent className="space-y-5">
                <div className="flex items-center">
                  {PREV_STEPS.map((s, i) => (
                    <Fragment key={s.label}>
                      <div className="flex flex-col items-center text-center">
                        <div className="flex h-9 w-9 items-center justify-center rounded-full border-2 border-primary bg-primary text-primary-foreground">
                          <s.Icon size={15} />
                        </div>
                        <span className="mt-1.5 text-[10px] font-medium">
                          {s.label}
                        </span>
                      </div>
                      {i < PREV_STEPS.length - 1 ? (
                        <div className="mx-2 mb-5 h-px flex-1 bg-primary/25" />
                      ) : null}
                    </Fragment>
                  ))}
                </div>
                <div className="grid grid-cols-2 gap-2">
                  {PREV_TILES.map((t) => (
                    <div
                      key={t.title}
                      className="rounded-md bg-muted/40 p-2.5 ring-1 ring-transparent transition-colors hover:ring-border"
                    >
                      <div className="flex items-center gap-2">
                        <div
                          className={cn(
                            "flex h-7 w-7 items-center justify-center rounded-md",
                            tint[t.c].bg,
                            tint[t.c].text,
                          )}
                        >
                          <t.Icon size={14} />
                        </div>
                        <span className="text-[11px] font-semibold">
                          {t.title}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
              <CardFooter className="border-t text-[11px] text-muted-foreground">
                <span className="nexus-live relative mr-2 h-1.5 w-1.5 rounded-full bg-emerald-500 text-emerald-500" />
                Live · last heartbeat 42s ago
              </CardFooter>
            </Card>
          </motion.div>
        </section>

        {/* Principles */}
        <section className="py-16">
          <Reveal>
            <SectionHead
              crumb="Design principles"
              title="The rules the whole system is built around."
            />
          </Reveal>
          <Stagger className="grid gap-4 sm:grid-cols-3">
            {PRINCIPLES.map((p) => (
              <MotionCard key={p.title}>
                <Card className="h-full">
                  <CardHeader>
                    <div
                      className={cn(
                        "flex h-9 w-9 items-center justify-center rounded-md",
                        tint[p.c].bg,
                        tint[p.c].text,
                      )}
                    >
                      <p.Icon size={18} />
                    </div>
                    <CardTitle className="mt-3 font-heading text-base">
                      {p.title}
                    </CardTitle>
                    <CardDescription className="mt-1.5 leading-relaxed">
                      {p.desc}
                    </CardDescription>
                  </CardHeader>
                </Card>
              </MotionCard>
            ))}
          </Stagger>
        </section>

        {/* Module catalog — every module, available or planned */}
        <section className="py-16">
          <Reveal>
            <SectionHead
              crumb="Module catalog"
              title="Every module Nexus is built around."
              desc="Available today or on the roadmap — toggle what you need per project."
            />
          </Reveal>
          <Reveal delay={0.05}>
            <Card>
              <CardHeader>
                <CardTitle className="font-heading">Modules</CardTitle>
                <CardDescription>
                  The shared services Nexus provides, wired through one control
                  plane.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {MODULES.map((m) => (
                    <div
                      key={m.key}
                      className={cn(
                        "rounded-md p-3 transition-colors",
                        m.status === "available"
                          ? "bg-muted/40 ring-1 ring-transparent hover:ring-border"
                          : "border border-dashed border-border bg-muted/20 opacity-70",
                      )}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div
                          className={cn(
                            "flex h-9 w-9 shrink-0 items-center justify-center rounded-md",
                            tint[m.c].bg,
                            tint[m.c].text,
                          )}
                        >
                          <m.Icon size={18} />
                        </div>
                        <div className="flex flex-col items-end gap-0.5">
                          <span className="text-sm font-semibold">
                            {m.title}
                          </span>
                          {m.status === "available" ? (
                            <span className="flex items-center gap-1 text-[11px] font-medium text-emerald-600 dark:text-emerald-400">
                              <span className="nexus-live relative h-1.5 w-1.5 rounded-full bg-emerald-500 text-emerald-500" />
                              Available
                            </span>
                          ) : (
                            <span className="text-[11px] text-muted-foreground">
                              Planned
                            </span>
                          )}
                        </div>
                      </div>
                      <p className="mt-2 text-pretty text-xs text-muted-foreground">
                        {m.desc}
                      </p>
                      <p className="mt-2 font-mono text-[10px] text-muted-foreground/80">
                        {m.key}
                      </p>
                    </div>
                  ))}
                </div>
              </CardContent>
              <CardFooter className="border-t text-xs text-muted-foreground">
                <span className="flex items-center gap-1.5">
                  <span className="nexus-live relative h-1.5 w-1.5 rounded-full bg-emerald-500 text-emerald-500" />
                  Available now
                </span>
                <span className="mx-2">·</span>
                <span>Dashed tiles are planned — on the roadmap, not yet built.</span>
              </CardFooter>
            </Card>
          </Reveal>
        </section>

        {/* How it works — readiness-stepper register */}
        <section className="py-16">
          <Reveal>
            <SectionHead
              crumb="How it works"
              title="Declare. Decide. Answer."
            />
          </Reveal>
          <Reveal delay={0.05}>
            <Card>
              <CardContent className="pt-6">
                <div className="flex flex-col items-stretch gap-6 sm:flex-row sm:items-center">
                  {STEPS.map((s, i) => (
                    <Fragment key={s.label}>
                      <div className="flex flex-1 flex-col items-center text-center">
                        <div className="flex h-10 w-10 items-center justify-center rounded-full border-2 border-primary bg-primary text-primary-foreground">
                          <s.Icon size={18} />
                        </div>
                        <span className="mt-2 text-sm font-semibold">
                          {s.label}
                        </span>
                        <span className="mt-1 text-xs text-muted-foreground">
                          {s.text}
                        </span>
                      </div>
                      {i < STEPS.length - 1 ? (
                        <div className="h-px w-full bg-primary/25 sm:w-auto sm:flex-1" />
                      ) : null}
                    </Fragment>
                  ))}
                </div>
              </CardContent>
            </Card>
          </Reveal>
        </section>

        {/* Self-hosted */}
        <section className="py-16">
          <Reveal>
            <SectionHead
              crumb="Yours"
              title="Self-hosted. Yours, completely."
              desc="A personal control plane — built to run on your own infrastructure and stay there."
            />
          </Reveal>
          <Stagger className="grid gap-4 sm:grid-cols-3">
            {SELF.map((p) => (
              <MotionCard key={p.title}>
                <Card className="h-full">
                  <CardContent className="pt-6">
                    <div
                      className={cn(
                        "flex h-9 w-9 items-center justify-center rounded-md",
                        tint[p.c].bg,
                        tint[p.c].text,
                      )}
                    >
                      <p.Icon size={18} />
                    </div>
                    <h3 className="mt-3 text-sm font-semibold">{p.title}</h3>
                    <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
                      {p.desc}
                    </p>
                  </CardContent>
                </Card>
              </MotionCard>
            ))}
          </Stagger>
        </section>

        {/* Closing CTA */}
        <section className="py-16">
          <Reveal>
            <Card className="overflow-hidden">
              <CardContent className="flex flex-col items-center gap-5 px-6 py-12 text-center">
                <h2 className="max-w-xl text-balance text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
                  Bring your apps to one center of gravity.
                </h2>
                <p className="max-w-md text-pretty text-muted-foreground">
                  Claim your instance and connect your first project in minutes.
                </p>
                <div className="flex flex-wrap justify-center gap-3">
                  <Button asChild className="h-11 px-5 text-sm">
                    <Link href="/login">
                      Open the console
                      <ArrowRight />
                    </Link>
                  </Button>
                  <Button asChild variant="outline" className="h-11 px-5 text-sm">
                    <Link href="/register">Create your account</Link>
                  </Button>
                </div>
              </CardContent>
            </Card>
          </Reveal>
        </section>
      </Stagger>

      {/* Footer */}
      <footer className="border-t border-border">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-3 px-6 py-6 text-xs text-muted-foreground sm:flex-row">
          <ThemedLogo height={18} />
          <span>v0.1 · Self-hosted control plane</span>
          <div className="flex gap-4">
            <Link href="/login" className="hover:text-foreground">
              Sign in
            </Link>
            <Link href="/register" className="hover:text-foreground">
              Create account
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
