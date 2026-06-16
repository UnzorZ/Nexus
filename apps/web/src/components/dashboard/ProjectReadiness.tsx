"use client";

import { Box, CheckCircle2, KeyRound, ShieldCheck } from "lucide-react";

const steps = [
  {
    id: 1,
    label: "Project created",
    status: "Complete",
    icon: CheckCircle2,
    completed: true,
  },
  {
    id: 2,
    label: "API key issued",
    status: "2 active",
    icon: KeyRound,
    completed: true,
  },
  {
    id: 3,
    label: "SDK connected",
    status: "Last heartbeat 42s ago",
    icon: Box,
    completed: true,
  },
  {
    id: 4,
    label: "Modules configured",
    status: "4 enabled",
    icon: ShieldCheck,
    completed: true,
  },
];

export function ProjectReadiness() {
  return (
    <div className="flex h-full flex-col rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-semibold text-slate-900">Project readiness</h2>

      <div className="mt-5 flex flex-1 items-center justify-between">
        <div className="flex flex-1 items-center">
          {steps.map((step, index) => {
            const Icon = step.icon;
            const isLast = index === steps.length - 1;

            return (
              <div key={step.id} className="flex flex-1 items-center">
                <div className="flex flex-col items-center">
                  <div
                    className={`flex h-10 w-10 items-center justify-center rounded-full border-2 ${
                      step.completed
                        ? "border-indigo-600 bg-indigo-600 text-white"
                        : "border-slate-200 bg-white text-slate-400"
                    }`}
                  >
                    <Icon className="h-5 w-5" />
                  </div>
                  <p className="mt-2 text-xs font-medium text-slate-900">
                    {step.id}. {step.label}
                  </p>
                  <p
                    className={`mt-0.5 text-xs ${
                      step.completed ? "text-emerald-600" : "text-slate-500"
                    }`}
                  >
                    {step.status}
                  </p>
                </div>

                {!isLast && (
                  <div className="mx-2 h-px flex-1 bg-indigo-200" />
                )}
              </div>
            );
          })}
        </div>

        <div className="ml-6 flex shrink-0 items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-2">
          <CheckCircle2 className="h-5 w-5 text-emerald-600" />
          <span className="text-sm font-semibold text-emerald-700">Ready</span>
        </div>
      </div>

      <div className="mt-5 flex items-center gap-2 border-t border-slate-100 pt-4">
        <span className="h-2 w-2 rounded-full bg-emerald-500" />
        <p className="text-xs text-slate-600">
          F-Shop can use enabled Nexus capabilities through the SDK.
        </p>
      </div>
    </div>
  );
}
