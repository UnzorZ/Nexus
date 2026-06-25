"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { AuthLogo } from "./AuthLogo";
import { ThemeToggle } from "@/components/ui/theme-toggle";

export type AuthShellMode = "login" | "register" | "minimal";

/**
 * Split-screen auth shell matching the modern SaaS login aesthetic:
 * - form panel on the left with branded header/footer
 * - immersive, rounded illustration on the right with light/dark variants
 *
 * Shared by /login, /register and the project OAuth /p/[slug]/login.
 */

function AuthVisual() {
  return (
    <aside
      className="relative hidden h-screen lg:block lg:w-1/2"
      aria-hidden="true"
    >
      <div className="h-full w-full px-4 pb-4 pt-24">
        <div
          className="h-full w-full rounded-3xl bg-cover bg-center bg-no-repeat"
          style={{ backgroundImage: "var(--auth-panel-image)" }}
        />
      </div>
    </aside>
  );
}

function AuthHeader({ mode }: { mode: AuthShellMode }) {
  if (mode !== "login" && mode !== "register") return null;

  return (
    <header className="absolute left-0 right-0 top-0 z-50 flex items-center justify-between px-6 py-5 sm:px-10">
      <AuthLogo height={28} />
      <div className="flex items-center gap-1">
        {mode === "login" ? (
          <Link
            href="/register"
            className="rounded-md px-2 py-1.5 text-sm font-medium text-foreground transition-colors hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10"
          >
            Sign up
          </Link>
        ) : (
          <Link
            href="/login"
            className="rounded-md px-2 py-1.5 text-sm font-medium text-foreground transition-colors hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10"
          >
            Sign in
          </Link>
        )}
        <ThemeToggle className="h-9 w-9" />
      </div>
    </header>
  );
}

export function AuthShell({
  children,
  mode = "minimal",
}: {
  children?: ReactNode;
  mode?: AuthShellMode;
}) {
  const showAuthChrome = mode === "login" || mode === "register";

  return (
    <div className="relative min-h-screen overflow-hidden bg-background">
      {showAuthChrome ? <AuthHeader mode={mode} /> : null}

      <div className="flex min-h-screen w-full">
        <section
          className={`flex min-h-screen w-full flex-col bg-white px-6 pb-8 dark:bg-background sm:px-10 lg:w-1/2 ${
            showAuthChrome ? "justify-center pt-24" : "justify-start pt-20"
          }`}
        >
          <div className="mx-auto w-full max-w-[420px]">{children}</div>

          {showAuthChrome ? (
            <footer className="mt-auto flex items-center justify-between pt-10 text-xs text-muted-foreground">
              <span>© 2026 Nexus</span>
              <div className="flex items-center gap-4">
                <Link href="#" className="hover:text-foreground">
                  Privacy
                </Link>
                <Link href="#" className="hover:text-foreground">
                  Terms
                </Link>
              </div>
            </footer>
          ) : null}
        </section>

        <AuthVisual />
      </div>
    </div>
  );
}
