"use client";

import { ClipboardCheckIcon } from "@/components/ui/clipboard-check";
import { TriangleAlertIcon } from "@/components/ui/triangle-alert-icon";
import {
  Panel,
  StatTile,
  StatusBadge,
} from "@/components/dashboard/shared";
import { RelatedLinks } from "@/components/dashboard/modules/ModuleShell";
import { tint } from "@/components/dashboard/anim";

const audited = [
  "Nexus account & project-user login",
  "API key create / disable / delete",
  "Module enable / disable",
  "Permission declaration & role changes",
  "Permission assignment changes",
  "Token & session revocation",
];

export function AuditModule() {
  return (
    <>
      <Panel title="Audit" description="Central, immutable trail of sensitive project actions.">
        <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-4">
          <StatTile Icon={ClipboardCheckIcon} iconBg={tint.amber.bg} iconColor={tint.amber.text} label="Events (24h)" value="412" hint="Recorded" />
          <StatTile Icon={TriangleAlertIcon} iconBg={tint.red.bg} iconColor={tint.red.text} label="Failures" value="23" hint="Denied / failed" />
          <StatTile Icon={ClipboardCheckIcon} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Retention" value="90d" hint="Then archived" />
          <StatTile Icon={ClipboardCheckIcon} iconBg={tint.indigo.bg} iconColor={tint.indigo.text} label="Storage" value="Postgres" hint="Append-only" />
        </div>
      </Panel>

      <RelatedLinks
        links={[
          { href: "/dashboard/audit", label: "Audit log", hint: "Browse & filter the full event trail" },
        ]}
      />

      <Panel title="Policy" description="What gets recorded and for how long.">
        <div className="flex flex-col gap-3">
          <div>
            <p className="mb-1.5 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
              Always audited
            </p>
            <ul className="flex flex-wrap gap-1.5">
              {audited.map((a) => (
                <li key={a}>
                  <StatusBadge tone="slate">{a}</StatusBadge>
                </li>
              ))}
            </ul>
          </div>
          <p className="border-t pt-3 text-xs leading-relaxed text-muted-foreground">
            Payloads are kept intentionally small — never full keys, passwords,
            refresh tokens or raw JWTs. Retention is 90 days hot, then archived.
          </p>
        </div>
      </Panel>
    </>
  );
}
