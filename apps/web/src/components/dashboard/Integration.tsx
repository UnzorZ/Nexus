"use client";

import { Copy } from "lucide-react";

const integrationData = {
  sdk: "Spring Boot starter",
  application: "f-shop-api",
  version: "1.4.2",
  lastHeartbeat: "42 seconds ago",
  apiKey: "nx_live_**********a1b2",
};

const configSnippet = `nexus:
  project: f-shop
  api-key: \${NEXUS_API_KEY}`;

export function Integration() {
  return (
    <div className="flex flex-col rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-semibold text-slate-900">Integration</h2>

      <div className="mt-4 flex flex-1 flex-col gap-4">
        <div className="min-w-0 space-y-2.5">
          <div className="flex items-center gap-3 text-sm">
            <span className="w-24 shrink-0 text-slate-500">SDK</span>
            <span className="font-medium text-slate-900">{integrationData.sdk}</span>
          </div>
          <div className="flex items-center gap-3 text-sm">
            <span className="w-24 shrink-0 text-slate-500">Application</span>
            <span className="font-medium text-slate-900">
              {integrationData.application}
            </span>
          </div>
          <div className="flex items-center gap-3 text-sm">
            <span className="w-24 shrink-0 text-slate-500">Version</span>
            <span className="font-medium text-slate-900">
              {integrationData.version}
            </span>
          </div>
          <div className="flex items-center gap-3 text-sm">
            <span className="w-24 shrink-0 text-slate-500">Last heartbeat</span>
            <span className="flex items-center gap-1.5 font-medium text-slate-900">
              <span className="h-2 w-2 rounded-full bg-emerald-500" />
              {integrationData.lastHeartbeat}
            </span>
          </div>
          <div className="flex items-center gap-3 text-sm">
            <span className="w-24 shrink-0 text-slate-500">API key</span>
            <span className="flex min-w-0 flex-1 items-center gap-2 font-medium text-slate-900">
              <span className="truncate">{integrationData.apiKey}</span>
              <button
                type="button"
                className="rounded p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
              >
                <Copy className="h-3.5 w-3.5" />
              </button>
            </span>
          </div>
        </div>

        <div className="w-full rounded-lg border border-slate-200 bg-slate-50 p-3 font-mono text-[11px] leading-relaxed text-slate-700">
          <pre className="overflow-x-auto">{configSnippet}</pre>
        </div>
      </div>

      <button
        type="button"
        className="mt-5 inline-flex h-8 items-center rounded-lg border border-indigo-200 bg-indigo-50 px-3 text-xs font-medium text-indigo-700 transition hover:bg-indigo-100"
      >
        View credentials
      </button>
    </div>
  );
}
