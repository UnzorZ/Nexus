"use client";

import { ArrowRight, Activity, ClipboardList, ShieldCheck, User } from "lucide-react";

const capabilities = [
  {
    id: "identity",
    title: "Identity",
    description: "Project-isolated users and OAuth/OIDC",
    metric: "248 users",
    icon: User,
    iconBg: "bg-indigo-50",
    iconColor: "text-indigo-600",
  },
  {
    id: "permissions",
    title: "Permissions",
    description: "Catalog, roles and assignments",
    metric: "18 declared",
    icon: ShieldCheck,
    iconBg: "bg-violet-50",
    iconColor: "text-violet-600",
  },
  {
    id: "registry",
    title: "Registry",
    description: "Application instances and heartbeat",
    metric: "1 online",
    icon: Activity,
    iconBg: "bg-cyan-50",
    iconColor: "text-cyan-600",
  },
  {
    id: "audit",
    title: "Audit",
    description: "Sensitive project actions",
    metric: "Always on",
    icon: ClipboardList,
    iconBg: "bg-amber-50",
    iconColor: "text-amber-600",
  },
];

export function EnabledCapabilities() {
  return (
    <div className="flex flex-col rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-semibold text-slate-900">Enabled capabilities</h2>

      <div className="mt-4 grid grid-cols-2 gap-4">
        {capabilities.map((cap) => {
          const Icon = cap.icon;
          return (
            <div
              key={cap.id}
              className="rounded-lg border border-slate-100 bg-slate-50/50 p-4 transition hover:border-slate-200"
            >
              <div className="flex items-start justify-between gap-2">
                <div
                  className={`flex h-9 w-9 items-center justify-center rounded-lg ${cap.iconBg}`}
                >
                  <Icon className={`h-5 w-5 ${cap.iconColor}`} />
                </div>
                <div className="flex items-center gap-2">
                  <h3 className="text-sm font-semibold text-slate-900">
                    {cap.title}
                  </h3>
                  <span className="text-xs text-slate-500">{cap.metric}</span>
                </div>
              </div>
              <p className="mt-2 text-xs text-slate-500">{cap.description}</p>
              <button
                type="button"
                className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-indigo-600 transition hover:text-indigo-700"
              >
                Open
                <ArrowRight className="h-3 w-3" />
              </button>
            </div>
          );
        })}
      </div>

      <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-4">
        <button
          type="button"
          className="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 transition hover:text-indigo-700"
        >
          Manage modules
          <ArrowRight className="h-3 w-3" />
        </button>
        <p className="text-xs text-slate-400">Notify is available to enable.</p>
      </div>
    </div>
  );
}
