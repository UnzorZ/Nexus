"use client";

import { useEffect, useRef, useState } from "react";
import { motion } from "motion/react";
import type { NexusAccount } from "@/features/accounts/api";
import { fetchCurrentAccount } from "@/features/session/api";
import { NexusApiError } from "@/lib/api/client";
import { buildPanelLoginUrl } from "@/lib/api/routes";
import { Sidebar } from "./Sidebar";
import { Topbar } from "./Topbar";

const MAX_WIDTH = 400;
const DEFAULT_WIDTH = 256;
const COLLAPSED_WIDTH = 64;
const EXPANDED_MIN_WIDTH = 200;
const COLLAPSE_THRESHOLD = 160;
const EXPAND_THRESHOLD = 180;
const COLLAPSED_STORAGE_KEY = "nexus-sidebar-collapsed";

/**
 * Opt-in session bypass for visual prototyping/QA. Set
 * `NEXT_PUBLIC_DEV_BYPASS=1` to render the dashboard shell with the mock
 * account below instead of calling the backend `/me` — useful when iterating on
 * page layouts without the API running. Never active in production builds.
 */
const DEV_BYPASS =
  process.env.NEXT_PUBLIC_DEV_BYPASS === "1" &&
  process.env.NODE_ENV !== "production";

const MOCK_ACCOUNT: NexusAccount = {
  id: "dev-account-0000",
  email: "unzor@unzor.xyz",
  displayName: "Marcos",
  status: "ACTIVE",
  mfaEnabled: false,
  instanceAdmin: true,
  emailVerifiedAt: null,
  lastLoginAt: null,
  createdAt: "2025-01-12T09:30:00Z",
  updatedAt: "2025-06-01T12:00:00Z",
};

function readSavedCollapsed(): boolean {
  if (typeof window === "undefined") return false;
  return localStorage.getItem(COLLAPSED_STORAGE_KEY) === "true";
}

type SessionGuardState =
  | { status: "checking" }
  | { status: "error"; message: string }
  | { status: "authenticated"; account: NexusAccount };

function currentPathForContinue(): string {
  if (typeof window === "undefined") return "/dashboard";
  const { pathname, search } = window.location;
  return `${pathname}${search}` || "/dashboard";
}

export function DashboardShell({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const [session, setSession] = useState<SessionGuardState>(() =>
    DEV_BYPASS
      ? { status: "authenticated", account: MOCK_ACCOUNT }
      : { status: "checking" },
  );
  const [expandedWidth, setExpandedWidth] = useState(DEFAULT_WIDTH);
  const [collapsed, setCollapsed] = useState(readSavedCollapsed);
  const [isResizing, setIsResizing] = useState(false);
  const startXRef = useRef(0);
  const startWidthRef = useRef(DEFAULT_WIDTH);
  const wasCollapsedRef = useRef(false);

  const sidebarWidth = collapsed ? COLLAPSED_WIDTH : expandedWidth;

  useEffect(() => {
    let cancelled = false;

    // Bypass initializes `session` as authenticated via useState; nothing to fetch.
    if (DEV_BYPASS) return;

    fetchCurrentAccount()
      .then((currentAccount) => {
        if (cancelled) return;
        if (!currentAccount) {
          window.location.href = buildPanelLoginUrl(currentPathForContinue());
          return;
        }
        setSession({ status: "authenticated", account: currentAccount });
      })
      .catch((loadError) => {
        if (cancelled) return;
        const message =
          loadError instanceof NexusApiError
            ? loadError.message
            : "No se pudo comprobar la sesión del panel.";
        setSession({ status: "error", message });
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") return;

    function handleMouseMove(event: MouseEvent) {
      if (!isResizing) return;

      const delta = event.clientX - startXRef.current;

      if (wasCollapsedRef.current) {
        const newWidth = COLLAPSED_WIDTH + delta;
        if (newWidth > EXPAND_THRESHOLD) {
          const expandedWidth = Math.min(
            Math.max(newWidth, EXPANDED_MIN_WIDTH),
            MAX_WIDTH
          );
          setCollapsed(false);
          setExpandedWidth(expandedWidth);
        }
      } else {
        const nextExpandedWidth = startWidthRef.current + delta;
        if (nextExpandedWidth < COLLAPSE_THRESHOLD) {
          setCollapsed(true);
        } else {
          setCollapsed(false);
          setExpandedWidth(
            Math.min(
              Math.max(nextExpandedWidth, EXPANDED_MIN_WIDTH),
              MAX_WIDTH
            )
          );
        }
      }
    }

    function handleMouseUp() {
      if (!isResizing) return;
      setIsResizing(false);
      document.body.style.userSelect = "";
      localStorage.setItem(COLLAPSED_STORAGE_KEY, String(collapsed));
    }

    window.addEventListener("mousemove", handleMouseMove);
    window.addEventListener("mouseup", handleMouseUp);

    return () => {
      window.removeEventListener("mousemove", handleMouseMove);
      window.removeEventListener("mouseup", handleMouseUp);
    };
  }, [isResizing, collapsed]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    localStorage.setItem(COLLAPSED_STORAGE_KEY, String(collapsed));
  }, [collapsed]);

  if (session.status === "checking") {
    return (
      <main className="flex min-h-screen items-center justify-center bg-muted/40 px-6 py-10">
        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="flex items-center gap-2 font-mono text-sm text-slate-500"
        >
          <span className="nexus-live relative h-2 w-2 rounded-full bg-primary text-primary" />
          Comprobando sesión...
        </motion.p>
      </main>
    );
  }

  if (session.status === "error") {
    return (
      <main className="flex min-h-screen items-center justify-center bg-muted/40 px-6 py-10">
        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="text-sm text-red-600"
        >
          {session.message}
        </motion.p>
      </main>
    );
  }

  function handleMouseDown(event: React.MouseEvent) {
    event.preventDefault();
    setIsResizing(true);
    startXRef.current = event.clientX;
    startWidthRef.current = expandedWidth;
    wasCollapsedRef.current = collapsed;
    document.body.style.userSelect = "none";
  }

  return (
    <div className="min-h-screen bg-muted/40">
      <Sidebar width={sidebarWidth} />
      <div
        role="separator"
        aria-orientation="vertical"
        aria-label="Resize sidebar"
        onMouseDown={handleMouseDown}
        className="fixed top-0 z-50 h-screen w-4 -translate-x-1/2 cursor-col-resize bg-transparent transition-colors hover:bg-primary/20 active:bg-primary/40"
        style={{ left: `${sidebarWidth}px` }}
      />
      <Topbar account={session.account} leftOffset={sidebarWidth} />
      <main
        className="mt-16 flex min-h-[calc(100vh-4rem)] flex-col p-6"
        style={{ marginLeft: `${sidebarWidth}px` }}
      >
        {children}
      </main>
    </div>
  );
}
