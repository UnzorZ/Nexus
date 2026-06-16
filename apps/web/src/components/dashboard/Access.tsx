"use client";

import { ArrowRight, Lock, User, Users } from "lucide-react";

const accessItems = [
  {
    id: "members",
    title: "Nexus members",
    value: "3",
    subtitle: "1 Owner · 2 Members",
    icon: Users,
    iconBg: "bg-indigo-50",
    iconColor: "text-indigo-600",
  },
  {
    id: "users",
    title: "Project users",
    value: "248",
    subtitle: "Identity realm",
    icon: User,
    iconBg: "bg-violet-50",
    iconColor: "text-violet-600",
  },
  {
    id: "clients",
    title: "OAuth clients",
    value: "2",
    subtitle: "Web app · Backend",
    icon: Lock,
    iconBg: "bg-amber-50",
    iconColor: "text-amber-600",
  },
];

export function Access() {
  return (
    <div className="flex flex-col rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-semibold text-slate-900">Access</h2>

      <div className="mt-4 grid flex-1 grid-cols-3 gap-4 divide-x divide-slate-100">
        {accessItems.map((item) => {
          const Icon = item.icon;
          return (
            <div
              key={item.id}
              className="flex flex-col px-2 first:pl-0 last:pr-0"
            >
              <div className="flex h-10 items-center gap-2">
                <div
                  className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${item.iconBg}`}
                >
                  <Icon className={`h-4 w-4 ${item.iconColor}`} />
                </div>
                <p className="text-xs font-medium leading-tight text-slate-600">
                  {item.title}
                </p>
              </div>
              <p className="mt-1 text-2xl font-semibold text-slate-900">
                {item.value}
              </p>
              <p className="mt-auto h-8 text-[11px] leading-tight text-slate-500">
                {item.subtitle}
              </p>
            </div>
          );
        })}
      </div>

      <div className="mt-auto border-t border-slate-100 pt-4">
        <button
          type="button"
          className="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 transition hover:text-indigo-700"
        >
          Manage access
          <ArrowRight className="h-3 w-3" />
        </button>
      </div>
    </div>
  );
}
