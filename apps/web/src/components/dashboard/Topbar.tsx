"use client";

import { useSyncExternalStore, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { useThemeReveal } from "@/components/ui/theme-toggle";
import { LogOut, Server, Settings, User } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { BellIcon } from "@/components/ui/bell";
import { MoonIcon } from "@/components/ui/moon-icon";
import { SearchIcon } from "@/components/ui/search";
import { SunIcon } from "@/components/ui/sun-icon";
import type { NexusAccount } from "@/features/accounts/api";
import { logoutPanelSession } from "@/features/session/api";
import { NexusApiError } from "@/lib/api/client";
import { animHandlers, type AnimIconHandle } from "./anim";

const notifications = [
  {
    id: 1,
    title: "API key near expiration",
    message: "demo-api key expires in 7 days.",
    time: "10 min ago",
    unread: true,
  },
  {
    id: 2,
    title: "New member joined",
    message: "Marcos added Ana to the project.",
    time: "2 h ago",
    unread: true,
  },
  {
    id: 3,
    title: "Heartbeat missed",
    message: "demo-project worker did not report.",
    time: "5 h ago",
    unread: false,
  },
];

function accountInitials(account: NexusAccount) {
  const source = account.displayName?.trim() || account.email;
  const words = source
    .replace(/@.*$/, "")
    .split(/[\s._-]+/)
    .filter(Boolean);

  if (words.length >= 2) {
    return `${words[0][0]}${words[1][0]}`.toUpperCase();
  }

  return source.slice(0, 2).toUpperCase();
}

export function Topbar({
  account,
  leftOffset,
}: {
  account: NexusAccount;
  leftOffset: number;
}) {
  const router = useRouter();
  const [logoutError, setLogoutError] = useState<string | null>(null);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const bellRef = useRef<AnimIconHandle>(null);
  const themeRef = useRef<AnimIconHandle>(null);
  const { resolvedTheme } = useTheme();
  const toggleTheme = useThemeReveal();

  // Detect client mount without setState-in-effect (the Sun/Moon icon depends
  // on the client-only resolvedTheme; this avoids a hydration mismatch).
  const mounted = useSyncExternalStore(
    () => () => {},
    () => true,
    () => false,
  );
  const isDark = mounted && resolvedTheme === "dark";

  const unreadCount = notifications.filter((n) => n.unread).length;
  const displayName = account.displayName?.trim() || account.email;
  const initials = accountInitials(account);

  async function handleLogout() {
    setLogoutError(null);
    setIsLoggingOut(true);

    try {
      await logoutPanelSession();
      router.push("/");
      router.refresh();
    } catch (error) {
      setLogoutError(
        error instanceof NexusApiError
          ? error.message
          : "No se pudo cerrar la sesión.",
      );
      setIsLoggingOut(false);
    }
  }

  return (
    <header
      className="fixed right-0 top-0 z-20 flex h-16 min-w-0 items-center justify-between gap-4 border-b border-border bg-card/80 px-6 backdrop-blur transition-[left] duration-150"
      style={{ left: `${leftOffset}px` }}
    >
      <div className="relative min-w-0 max-w-md flex-1">
        <SearchIcon
          size={16}
          className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground"
        />
        <Input
          type="text"
          placeholder="Search Nexus..."
          aria-label="Search Nexus"
          className="h-9 pl-9 pr-16"
        />
        <kbd className="pointer-events-none absolute top-1/2 right-3 hidden -translate-y-1/2 rounded border border-border bg-muted px-1.5 py-0.5 font-mono text-[10px] font-medium text-muted-foreground sm:inline">
          ⌘K
        </kbd>
      </div>

      <div className="flex shrink-0 items-center gap-2">
        <button
          type="button"
          aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
          {...animHandlers(themeRef)}
          onClick={toggleTheme}
          className="relative flex size-9 items-center justify-center rounded-lg text-muted-foreground transition-[color,background-color,transform] duration-150 hover:bg-muted hover:text-foreground active:scale-[0.96]"
        >
          {isDark ? (
            <SunIcon ref={themeRef} size={18} />
          ) : (
            <MoonIcon ref={themeRef} size={18} />
          )}
        </button>

        <DropdownMenu modal={false}>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              aria-label="Notifications"
              {...animHandlers(bellRef)}
              className="relative flex size-9 items-center justify-center rounded-lg text-muted-foreground transition-[color,background-color,transform] duration-150 hover:bg-muted hover:text-foreground active:scale-[0.96]"
            >
              <BellIcon ref={bellRef} size={18} />
              {unreadCount > 0 ? (
                <span className="nexus-blink tabular-nums absolute -top-0.5 -right-0.5 flex h-4 min-w-[16px] items-center justify-center rounded-full bg-primary px-1 text-[10px] font-semibold text-primary-foreground ring-2 ring-card">
                  {unreadCount}
                </span>
              ) : null}
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-80">
            <div className="flex items-center justify-between px-1 py-1">
              <span className="text-sm font-semibold">Notifications</span>
              <button
                type="button"
                className="text-xs font-medium text-primary hover:underline"
              >
                Mark all read
              </button>
            </div>
            <DropdownMenuSeparator />
            {notifications.map((notification) => (
              <DropdownMenuItem
                key={notification.id}
                className="flex-col items-start gap-0.5 py-2.5"
              >
                <div className="flex w-full items-start justify-between gap-2">
                  <span className="text-sm font-medium">{notification.title}</span>
                  {notification.unread ? (
                    <span className="mt-1.5 size-2 shrink-0 rounded-full bg-primary" />
                  ) : null}
                </div>
                <span className="text-xs text-muted-foreground">
                  {notification.message}
                </span>
                <span className="text-[11px] text-muted-foreground/70">
                  {notification.time}
                </span>
              </DropdownMenuItem>
            ))}
            <DropdownMenuSeparator />
            <DropdownMenuItem className="justify-center text-xs font-medium text-primary">
              View all notifications
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>

        <DropdownMenu modal={false}>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              aria-label="Open account menu"
              className="flex size-9 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary transition-[color,background-color,transform] duration-150 hover:bg-primary/20 active:scale-[0.96]"
            >
              {initials}
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel className="flex flex-col gap-0.5">
              <span className="text-sm font-semibold">{displayName}</span>
              <span className="truncate text-xs font-normal text-muted-foreground">
                {account.email}
              </span>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem className="gap-2">
              <User className="size-4" />
              Profile
            </DropdownMenuItem>
            <DropdownMenuItem className="gap-2">
              <Settings className="size-4" />
              Account settings
            </DropdownMenuItem>
            {account.instanceAdmin ? (
              <>
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild className="gap-2">
                  <Link href="/instance-settings">
                    <Server className="size-4" />
                    Instance settings
                  </Link>
                </DropdownMenuItem>
              </>
            ) : null}
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={handleLogout}
              disabled={isLoggingOut}
              className="gap-2 text-destructive focus:text-destructive"
            >
              <LogOut className="size-4" />
              {isLoggingOut ? "Signing out..." : "Sign out"}
            </DropdownMenuItem>
            {logoutError ? (
              <p className="px-2 pb-1 text-xs leading-relaxed text-destructive">
                {logoutError}
              </p>
            ) : null}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
