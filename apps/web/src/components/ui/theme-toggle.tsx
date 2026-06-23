"use client";

import { useSyncExternalStore } from "react";
import { flushSync } from "react-dom";
import { useTheme } from "next-themes";
import { Moon, Sun } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Logo } from "@/components/landing/Logo";

/** Client-only mount flag without setState-in-effect (matches the Topbar
 *  pattern): false on the server, true after hydration. */
export function useMounted() {
  return useSyncExternalStore(
    () => () => {},
    () => true,
    () => false,
  );
}

/**
 * Circular-reveal theme toggle backed by the View Transitions API. The new theme
 * sweeps in as a growing circle from the click position (see the `nexus-theme-reveal`
 * keyframe and `--reveal-x/y/r` custom properties in globals.css). Falls back to an
 * instant switch under `prefers-reduced-motion` or where `startViewTransition` is
 * unavailable. Shared by the login/landing `ThemeToggle` and the dashboard `Topbar`
 * so the effect is identical everywhere instead of reimplemented per toggle.
 */
export function useThemeReveal() {
  const { resolvedTheme, setTheme } = useTheme();
  return function toggleTheme(event?: { clientX: number; clientY: number }) {
    const target = resolvedTheme === "dark" ? "light" : "dark";
    const reduce =
      typeof window !== "undefined" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const doc = document as Document & {
      startViewTransition?: (cb: () => void) => void;
    };
    if (reduce || !doc.startViewTransition) {
      setTheme(target);
      return;
    }
    const x = event?.clientX ?? window.innerWidth / 2;
    const y = event?.clientY ?? window.innerHeight / 2;
    const endRadius = Math.hypot(
      Math.max(x, window.innerWidth - x),
      Math.max(y, window.innerHeight - y),
    );
    const root = document.documentElement;
    root.style.setProperty("--reveal-x", `${x}px`);
    root.style.setProperty("--reveal-y", `${y}px`);
    root.style.setProperty("--reveal-r", `${endRadius}px`);
    doc.startViewTransition(() => {
      flushSync(() => setTheme(target));
    });
  };
}

/** Light/dark toggle button (Sun in dark, Moon in light) with the circular
 *  reveal animation. */
export function ThemeToggle({ className }: { className?: string }) {
  const toggle = useThemeReveal();
  const { resolvedTheme } = useTheme();
  const mounted = useMounted();
  const dark = mounted && resolvedTheme === "dark";
  return (
    <Button
      variant="ghost"
      size="icon"
      className={className ?? "h-9 w-9"}
      aria-label={dark ? "Switch to light mode" : "Switch to dark mode"}
      onClick={toggle}
    >
      {dark ? <Sun size={16} /> : <Moon size={16} />}
    </Button>
  );
}

/** The real transparent brand lockup on light surfaces; icon + light wordmark
 *  on dark (the full logo's wordmark is near-black and vanishes on dark). */
export function ThemedLogo({ height = 26 }: { height?: number }) {
  const { resolvedTheme } = useTheme();
  const mounted = useMounted();
  const dark = mounted && resolvedTheme === "dark";
  return <Logo variant={dark ? "lockup" : "full"} height={height} />;
}
