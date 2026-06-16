"use client";

import { AlertTriangle, ArrowRight } from "lucide-react";

export function OAuthSetupCard() {
  return (
    <div className="rounded-xl border border-amber-200 bg-amber-50/50 p-5">
      <div className="flex items-start gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-amber-100 text-amber-600">
          <AlertTriangle className="h-5 w-5" />
        </div>
        <div>
          <h3 className="text-sm font-semibold text-slate-900">
            Complete OAuth setup
          </h3>
          <p className="mt-1 text-xs leading-relaxed text-slate-600">
            Add redirect URIs to your web client.
          </p>
          <button
            type="button"
            className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 transition hover:text-indigo-700"
          >
            Configure
            <ArrowRight className="h-3 w-3" />
          </button>
        </div>
      </div>
    </div>
  );
}
