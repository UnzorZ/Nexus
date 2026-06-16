"use client";

import {
  BookOpen,
  FileText,
  KeyRound,
  Plus,
  Users,
  Zap,
} from "lucide-react";

const actions = [
  {
    id: "api-key",
    label: "Create API key",
    description: "Generate a new project key",
    icon: KeyRound,
    iconBg: "bg-indigo-50",
    iconColor: "text-indigo-600",
  },
  {
    id: "member",
    label: "Add member",
    description: "Invite someone to the project",
    icon: Users,
    iconBg: "bg-emerald-50",
    iconColor: "text-emerald-600",
  },
  {
    id: "guide",
    label: "Integration guide",
    description: "Connect your application",
    icon: BookOpen,
    iconBg: "bg-violet-50",
    iconColor: "text-violet-600",
  },
  {
    id: "docs",
    label: "API docs",
    description: "Browse the API reference",
    icon: FileText,
    iconBg: "bg-amber-50",
    iconColor: "text-amber-600",
  },
];

export function QuickActions() {
  return (
    <div className="flex flex-1 flex-col rounded-xl border border-slate-200 bg-white p-5">
      <div className="flex items-center gap-2">
        <Zap className="h-4 w-4 text-slate-500" />
        <h2 className="text-sm font-semibold text-slate-900">Quick actions</h2>
      </div>

      <div className="mt-4 flex-1 space-y-2">
        {actions.map((action) => {
          const Icon = action.icon;
          return (
            <button
              key={action.id}
              type="button"
              className="flex w-full items-center gap-3 rounded-lg p-2 text-left transition hover:bg-slate-50"
            >
              <div
                className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${action.iconBg}`}
              >
                <Icon className={`h-4 w-4 ${action.iconColor}`} />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-slate-900">
                  {action.label}
                </p>
                <p className="text-xs text-slate-500">{action.description}</p>
              </div>
              <Plus className="h-4 w-4 shrink-0 text-slate-300" />
            </button>
          );
        })}
      </div>
    </div>
  );
}
