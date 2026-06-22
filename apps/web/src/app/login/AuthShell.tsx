"use client";

import type { CSSProperties, ReactNode } from "react";
import { Code2, Layers, ShieldCheck } from "lucide-react";
import { ThemedLogo, ThemeToggle } from "@/components/ui/theme-toggle";

/**
 * Split-screen auth shell: a procedural visual panel on the left (a
 * placeholder for a real image — pass `imageSrc` to swap one in) and a
 * theme-aware form panel on the right. Shared by /login, /register and the
 * project OAuth /p/[slug]/login.
 */

const FEATURES = [
  { Icon: ShieldCheck, label: "Project isolation" },
  { Icon: Layers, label: "Reusable modules" },
  { Icon: Code2, label: "SDK integration" },
];

const PANEL_BG: CSSProperties = {
  backgroundColor: "#0c0a1c",
  backgroundImage:
    "radial-gradient(circle, rgba(255,255,255,0.06) 1px, transparent 1.3px)," +
    "radial-gradient(120% 90% at 14% 4%, rgba(109,77,255,0.55), transparent 55%)," +
    "radial-gradient(90% 80% at 96% 96%, rgba(76,217,230,0.16), transparent 60%)," +
    "linear-gradient(155deg, #1b1546 0%, #120d2e 46%, #08070f 100%)",
  backgroundSize: "24px 24px, 100% 100%, 100% 100%, 100% 100%",
};

function RingsEmblem({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 200 200"
      fill="none"
      aria-hidden="true"
    >
      <g
        fill="none"
        stroke="rgba(196,178,255,0.22)"
        strokeWidth="2.2"
        strokeLinecap="round"
      >
        <circle cx="78" cy="84" r="46" />
        <circle cx="122" cy="84" r="46" />
        <circle cx="100" cy="122" r="46" />
      </g>
    </svg>
  );
}

function AuthVisual({ imageSrc }: { imageSrc?: string }) {
  return (
    <aside className="relative hidden overflow-hidden lg:block" aria-hidden="true">
      {imageSrc ? (
        // Swap in a real photo/render anytime: <AuthVisual imageSrc="/login-panel.png" />
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={imageSrc}
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
        />
      ) : (
        <>
          <div className="absolute inset-0" style={PANEL_BG} />
          <RingsEmblem className="absolute right-[-6rem] top-1/2 h-[34rem] w-[34rem] -translate-y-1/2 opacity-70 [filter:drop-shadow(0_0_60px_rgba(109,77,255,0.35))]" />
          <div
            className="absolute inset-0"
            style={{
              background:
                "radial-gradient(125% 100% at 50% 45%, transparent 52%, rgba(0,0,0,0.55) 100%)",
            }}
          />
        </>
      )}

      <div className="relative z-10 flex h-full flex-col justify-between p-10 xl:p-14">
        <div className="inline-flex items-center gap-2.5 font-mono text-sm font-semibold tracking-[0.22em] text-white">
          <span className="h-2.5 w-2.5 rounded-full bg-[var(--accent,#6d4dff)] shadow-[0_0_0_4px_rgba(109,77,255,0.25),0_0_14px_2px_rgba(109,77,255,0.7)]" />
          NEXUS
        </div>

        <div className="max-w-md">
          <h2 className="text-3xl font-semibold leading-tight tracking-tight text-white xl:text-4xl">
            Your projects. One control plane.
          </h2>
          <p className="mt-4 max-w-sm text-base leading-relaxed text-white/65">
            Manage shared identity, permissions, API keys and reusable modules
            from one place.
          </p>
          <ul className="mt-9 space-y-4">
            {FEATURES.map(({ Icon, label }) => (
              <li key={label} className="flex items-center gap-3 text-white/85">
                <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/10 ring-1 ring-white/15">
                  <Icon size={17} className="text-white" />
                </span>
                <span className="text-[0.95rem]">{label}</span>
              </li>
            ))}
          </ul>
        </div>

        <div className="flex flex-col gap-1 text-xs text-white/45">
          <strong className="font-medium text-white/70">
            Nexus Control Plane
          </strong>
          <span>Self-hosted</span>
        </div>
      </div>
    </aside>
  );
}

export function AuthShell({ children }: { children?: ReactNode }) {
  return (
    <div className="grid min-h-screen bg-background lg:grid-cols-[1.08fr_0.92fr]">
      <AuthVisual />
      <section className="relative flex min-h-screen flex-col">
        <div className="flex items-center justify-between px-6 py-5 sm:px-10">
          <ThemedLogo height={24} />
          <ThemeToggle />
        </div>
        <div className="flex flex-1 items-center justify-center px-6 pb-12 sm:px-10">
          <div className="w-full max-w-sm">{children}</div>
        </div>
      </section>
    </div>
  );
}
