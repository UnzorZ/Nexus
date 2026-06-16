"use client";

import { Activity, ArrowRight, KeyRound, ShieldCheck, User } from "lucide-react";

const activities = [
  {
    id: 1,
    message: "Permissions synchronized",
    actor: "f-shop-api",
    time: "2 minutes ago",
    icon: ShieldCheck,
    iconColor: "text-indigo-600",
    iconBg: "bg-indigo-50",
  },
  {
    id: 2,
    message: "Heartbeat received",
    actor: "f-shop-api",
    time: "42 seconds ago",
    icon: Activity,
    iconColor: "text-emerald-600",
    iconBg: "bg-emerald-50",
  },
  {
    id: 3,
    message: "API key created",
    actor: "Marcos",
    time: "3 days ago",
    icon: KeyRound,
    iconColor: "text-amber-600",
    iconBg: "bg-amber-50",
  },
  {
    id: 4,
    message: "Identity module enabled",
    actor: "Marcos",
    time: "5 days ago",
    icon: User,
    iconColor: "text-violet-600",
    iconBg: "bg-violet-50",
  },
];

export function RecentActivity() {
  return (
    <div className="flex flex-col rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-semibold text-slate-900">Recent activity</h2>

      <ul className="mt-4 flex-1 space-y-3">
        {activities.map((activity) => {
          const Icon = activity.icon;
          return (
            <li
              key={activity.id}
              className="flex items-center justify-between gap-3"
            >
              <div className="flex items-center gap-3">
                <div
                  className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${activity.iconBg}`}
                >
                  <Icon className={`h-4 w-4 ${activity.iconColor}`} />
                </div>
                <p className="text-sm font-medium text-slate-900">
                  {activity.message}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3 text-xs text-slate-500">
                <span>{activity.actor}</span>
                <span className="text-slate-300">·</span>
                <span>{activity.time}</span>
              </div>
            </li>
          );
        })}
      </ul>

      <div className="mt-auto border-t border-slate-100 pt-4">
        <button
          type="button"
          className="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 transition hover:text-indigo-700"
        >
          View audit log
          <ArrowRight className="h-3 w-3" />
        </button>
      </div>
    </div>
  );
}
