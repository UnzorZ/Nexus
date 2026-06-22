"use client";

import { useSyncExternalStore } from "react";
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

/** Light/dark toggle button (Sun in dark, Moon in light). */
export function ThemeToggle({ className }: { className?: string }) {
  const { resolvedTheme, setTheme } = useTheme();
  const mounted = useMounted();
  const dark = mounted && resolvedTheme === "dark";
  return (
    <Button
      variant="ghost"
      size="icon"
      className={className ?? "h-9 w-9"}
      aria-label={dark ? "Switch to light mode" : "Switch to dark mode"}
      onClick={() => setTheme(dark ? "light" : "dark")}
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
