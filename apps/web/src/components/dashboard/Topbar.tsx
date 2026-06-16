"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Bell, LogOut, Search, Settings, User } from "lucide-react";
import type { NexusAccount } from "@/features/accounts/api";
import { logoutPanelSession } from "@/features/session/api";
import { NexusApiError } from "@/lib/api/client";

const notifications = [
  {
    id: 1,
    title: "API key near expiration",
    message: "f-shop-api key expires in 7 days.",
    time: "10 min ago",
    unread: true,
  },
  {
    id: 2,
    title: "New member joined",
    message: "Marcos added Ana to F-Shop.",
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
  const [isNotificationsOpen, setIsNotificationsOpen] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const [logoutError, setLogoutError] = useState<string | null>(null);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

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
      className="fixed right-0 top-0 z-20 flex h-16 min-w-0 items-center justify-between gap-4 border-b border-slate-200 bg-white px-6 transition-[left] duration-150"
      style={{ left: `${leftOffset}px` }}
    >
      <div className="relative min-w-0 flex-1 max-w-md">
        <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
          <Search className="h-4 w-4 text-slate-400" />
        </div>
        <input
          type="text"
          placeholder="Search Nexus..."
          className="h-9 w-full rounded-lg border border-slate-200 bg-white pl-9 pr-16 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
        />
        <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-3">
          <kbd className="hidden rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-[10px] font-medium text-slate-500 sm:inline">
            ⌘K
          </kbd>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-3">
        <div className="relative">
          <button
            type="button"
            onClick={() => {
              setIsNotificationsOpen(!isNotificationsOpen);
              setIsProfileOpen(false);
            }}
            className="relative flex h-9 w-9 items-center justify-center rounded-lg text-slate-500 transition hover:bg-slate-100 hover:text-slate-700"
          >
            <Bell className="h-[18px] w-[18px]" />
            {unreadCount > 0 ? (
              <span className="absolute right-2 top-1.5 flex h-4 min-w-[16px] items-center justify-center rounded-full bg-indigo-600 px-1 text-[10px] font-semibold text-white">
                {unreadCount}
              </span>
            ) : null}
          </button>

          {isNotificationsOpen ? (
            <div className="absolute right-0 top-full z-50 mt-2 w-80 rounded-lg border border-slate-200 bg-white py-2 shadow-lg">
              <div className="flex items-center justify-between px-4 py-2">
                <p className="text-sm font-semibold text-slate-900">
                  Notifications
                </p>
                <button
                  type="button"
                  className="text-xs font-medium text-indigo-600 hover:text-indigo-700"
                >
                  Mark all read
                </button>
              </div>
              <div className="border-t border-slate-100" />
              {notifications.map((notification) => (
                <button
                  key={notification.id}
                  type="button"
                  className="flex w-full flex-col gap-0.5 px-4 py-3 text-left transition hover:bg-slate-50"
                >
                  <div className="flex items-start justify-between gap-2">
                    <p className="text-sm font-medium text-slate-900">
                      {notification.title}
                    </p>
                    {notification.unread ? (
                      <span className="mt-1.5 h-2 w-2 rounded-full bg-indigo-600" />
                    ) : null}
                  </div>
                  <p className="text-xs text-slate-500">
                    {notification.message}
                  </p>
                  <p className="text-[11px] text-slate-400">
                    {notification.time}
                  </p>
                </button>
              ))}
              <div className="border-t border-slate-100" />
              <button
                type="button"
                className="w-full px-4 py-2 text-left text-xs font-medium text-indigo-600 transition hover:bg-slate-50"
              >
                View all notifications
              </button>
            </div>
          ) : null}
        </div>

        <div className="relative">
          <button
            type="button"
            onClick={() => {
              setIsProfileOpen(!isProfileOpen);
              setIsNotificationsOpen(false);
            }}
            className="flex h-9 w-9 items-center justify-center rounded-full bg-indigo-100 text-sm font-semibold text-indigo-700 transition hover:bg-indigo-200"
            aria-label="Open account menu"
          >
            {initials}
          </button>

          {isProfileOpen ? (
            <div className="absolute right-0 top-full z-50 mt-2 w-56 rounded-lg border border-slate-200 bg-white py-2 shadow-lg">
              <div className="px-4 py-2">
                <p className="text-sm font-semibold text-slate-900">
                  {displayName}
                </p>
                <p className="truncate text-xs text-slate-500">
                  {account.email}
                </p>
              </div>
              <div className="border-t border-slate-100" />
              <button
                type="button"
                className="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-slate-700 transition hover:bg-slate-50"
              >
                <User className="h-4 w-4" />
                Profile
              </button>
              <button
                type="button"
                className="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-slate-700 transition hover:bg-slate-50"
              >
                <Settings className="h-4 w-4" />
                Account settings
              </button>
              <div className="border-t border-slate-100" />
              <button
                type="button"
                onClick={handleLogout}
                disabled={isLoggingOut}
                className="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-red-600 transition hover:bg-slate-50"
              >
                <LogOut className="h-4 w-4" />
                {isLoggingOut ? "Signing out..." : "Sign out"}
              </button>
              {logoutError ? (
                <p className="px-4 pb-2 text-xs leading-relaxed text-red-600">
                  {logoutError}
                </p>
              ) : null}
            </div>
          ) : null}
        </div>
      </div>
    </header>
  );
}
