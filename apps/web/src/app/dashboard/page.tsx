"use client";

import { useState } from "react";
import {
  AlertTriangle,
  BookOpen,
  ChevronRight,
  Info,
  Plus,
  Settings,
  ShieldAlert,
  X,
} from "lucide-react";
import { Access } from "@/components/dashboard/Access";
import { EnabledCapabilities } from "@/components/dashboard/EnabledCapabilities";
import { Integration } from "@/components/dashboard/Integration";
import { ProjectReadiness } from "@/components/dashboard/ProjectReadiness";
import { RecentActivity } from "@/components/dashboard/RecentActivity";
import { QuickActions } from "@/components/dashboard/QuickActions";

type Alert = {
  id: string;
  variant: "warning" | "error" | "info";
  title: string;
  message: string;
  action: string;
};

const initialAlerts: Alert[] = [
  {
    id: "oauth",
    variant: "warning",
    title: "Complete OAuth setup",
    message: "Add redirect URIs to your web client.",
    action: "Configure",
  },
];

const alertStyles = {
  warning: {
    border: "border-amber-200",
    bg: "bg-amber-50/50",
    iconBg: "bg-amber-100",
    iconColor: "text-amber-600",
  },
  error: {
    border: "border-red-200",
    bg: "bg-red-50/50",
    iconBg: "bg-red-100",
    iconColor: "text-red-600",
  },
  info: {
    border: "border-blue-200",
    bg: "bg-blue-50/50",
    iconBg: "bg-blue-100",
    iconColor: "text-blue-600",
  },
};

function AlertIcon({ variant }: { variant: Alert["variant"] }) {
  if (variant === "error") return <ShieldAlert className="h-5 w-5" />;
  if (variant === "info") return <Info className="h-5 w-5" />;
  return <AlertTriangle className="h-5 w-5" />;
}

export default function DashboardPage() {
  const [alerts, setAlerts] = useState<Alert[]>(initialAlerts);
  const [isAlertsModalOpen, setIsAlertsModalOpen] = useState(false);

  const visibleAlerts = alerts.slice(0, 3);
  const hiddenCount = Math.max(0, alerts.length - 3);
  const hasAlerts = alerts.length > 0;

  function dismissAlert(id: string) {
    setAlerts((prev) => prev.filter((alert) => alert.id !== id));
  }

  function spawnAlert() {
    const variants: Alert["variant"][] = ["warning", "error", "info"];
    const variant = variants[alerts.length % variants.length];
    const nextId = alerts.length + 1;
    const newAlert: Alert =
      variant === "error"
        ? {
            id: `alert-${nextId}`,
            variant,
            title: `Critical alert ${nextId}`,
            message: "A service reported multiple failed heartbeats.",
            action: "Investigate",
          }
        : variant === "info"
          ? {
              id: `alert-${nextId}`,
              variant,
              title: `Information ${nextId}`,
              message: "A new SDK version is available for this project.",
              action: "Read more",
            }
          : {
              id: `alert-${nextId}`,
              variant,
              title: `Action required ${nextId}`,
              message: "Review pending permissions for new project members.",
              action: "Review",
            };
    setAlerts((prev) => [...prev, newAlert]);
  }

  return (
    <div className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <nav className="flex items-center gap-2 text-sm text-slate-500">
        <span>Projects</span>
        <ChevronRight className="h-3.5 w-3.5" />
        <span className="text-slate-900">F-Shop</span>
      </nav>

      <div className="mt-4 flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold tracking-tight text-slate-900">
              F-Shop
            </h1>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-700">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
              Active
            </span>
          </div>
          <p className="mt-1 text-sm text-slate-500">
            Project control plane and integration overview.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            className="inline-flex h-9 items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
          >
            <Settings className="h-4 w-4" />
            Project settings
          </button>
          <button
            type="button"
            className="inline-flex h-9 items-center gap-2 rounded-lg bg-indigo-600 px-3 text-sm font-medium text-white transition hover:bg-indigo-700"
          >
            <BookOpen className="h-4 w-4" />
            Integration guide
          </button>
        </div>
      </div>

      {hasAlerts ? (
        <div className="mt-6 grid flex-1 grid-cols-12 items-start gap-6">
          <div className="col-span-12 flex flex-col gap-6 lg:col-span-9">
            <ProjectReadiness />

            <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
              <EnabledCapabilities />
              <Integration />
              <Access />
              <RecentActivity />
            </div>
          </div>

          <div className="col-span-12 flex flex-col gap-6 lg:col-span-3">
            <div className="space-y-4">
              {visibleAlerts.map((alert) => {
                const styles = alertStyles[alert.variant];
                return (
                  <div
                    key={alert.id}
                    className={`relative rounded-xl border ${styles.border} ${styles.bg} p-5`}
                  >
                    <button
                      type="button"
                      onClick={() => dismissAlert(alert.id)}
                      className="absolute right-2 top-2 flex h-6 w-6 items-center justify-center rounded-md text-slate-400 transition hover:bg-slate-200/50 hover:text-slate-600"
                      aria-label="Dismiss alert"
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                    <div className="flex items-start gap-3 pr-4">
                      <div
                        className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${styles.iconBg} ${styles.iconColor}`}
                      >
                        <AlertIcon variant={alert.variant} />
                      </div>
                      <div>
                        <h3 className="text-sm font-semibold text-slate-900">
                          {alert.title}
                        </h3>
                        <p className="mt-1 text-xs leading-relaxed text-slate-600">
                          {alert.message}
                        </p>
                        <button
                          type="button"
                          className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 transition hover:text-indigo-700"
                        >
                          {alert.action}
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}

              {hiddenCount > 0 ? (
                <button
                  type="button"
                  onClick={() => setIsAlertsModalOpen(true)}
                  className="w-full rounded-lg border border-slate-200 bg-white py-2 text-xs font-medium text-slate-600 transition hover:bg-slate-50"
                >
                  View {hiddenCount} more alert{hiddenCount === 1 ? "" : "s"}
                </button>
              ) : null}
            </div>

            <button
              type="button"
              onClick={spawnAlert}
              className="inline-flex w-full items-center justify-center gap-1.5 rounded-lg border border-dashed border-slate-300 bg-slate-50 py-2 text-xs font-medium text-slate-500 transition hover:border-slate-400 hover:bg-slate-100 hover:text-slate-700"
            >
              <Plus className="h-3.5 w-3.5" />
              Add test alert
            </button>

            <div className="sticky top-24">
              <QuickActions />
            </div>
          </div>
        </div>
      ) : (
        <div className="mt-6 grid flex-1 grid-cols-12 items-start gap-6">
          <div className="col-span-12">
            <ProjectReadiness />
          </div>

          <div className="col-span-12 grid grid-cols-1 gap-6 lg:col-span-9 lg:grid-cols-2">
            <EnabledCapabilities />
            <Integration />
            <Access />
            <RecentActivity />
          </div>

          <div className="col-span-12 flex min-h-full flex-col gap-6 lg:col-span-3">
            <button
              type="button"
              onClick={spawnAlert}
              className="inline-flex w-full items-center justify-center gap-1.5 rounded-lg border border-dashed border-slate-300 bg-slate-50 py-2 text-xs font-medium text-slate-500 transition hover:border-slate-400 hover:bg-slate-100 hover:text-slate-700"
            >
              <Plus className="h-3.5 w-3.5" />
              Add test alert
            </button>

            <div className="sticky top-24">
              <QuickActions />
            </div>
          </div>
        </div>
      )}

      {isAlertsModalOpen ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
          onClick={() => setIsAlertsModalOpen(false)}
        >
          <div
            className="max-h-[80vh] w-full max-w-md overflow-hidden rounded-xl bg-white shadow-xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
              <h2 className="text-base font-semibold text-slate-900">
                All alerts ({alerts.length})
              </h2>
              <button
                type="button"
                onClick={() => setIsAlertsModalOpen(false)}
                className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="max-h-[60vh] space-y-4 overflow-y-auto p-5">
              {alerts.map((alert) => {
                const styles = alertStyles[alert.variant];
                return (
                  <div
                    key={alert.id}
                    className={`relative rounded-lg border ${styles.border} ${styles.bg} p-4`}
                  >
                    <button
                      type="button"
                      onClick={() => dismissAlert(alert.id)}
                      className="absolute right-2 top-2 flex h-6 w-6 items-center justify-center rounded-md text-slate-400 transition hover:bg-slate-200/50 hover:text-slate-600"
                      aria-label="Dismiss alert"
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                    <div className="flex items-start gap-3 pr-4">
                      <div
                        className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${styles.iconBg} ${styles.iconColor}`}
                      >
                        <AlertIcon variant={alert.variant} />
                      </div>
                      <div>
                        <h3 className="text-sm font-semibold text-slate-900">
                          {alert.title}
                        </h3>
                        <p className="mt-0.5 text-xs leading-relaxed text-slate-600">
                          {alert.message}
                        </p>
                        <button
                          type="button"
                          className="mt-2 inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 transition hover:text-indigo-700"
                        >
                          {alert.action}
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
