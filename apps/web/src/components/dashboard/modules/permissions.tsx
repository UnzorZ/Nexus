"use client";

import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { UserCogIcon } from "@/components/ui/user-cog-icon";
import { Button } from "@/components/ui/button";
import {
  MonoChip,
  Panel,
  StatTile,
  StatusBadge,
} from "@/components/dashboard/shared";
import { tint } from "@/components/dashboard/anim";

export function PermissionsModule() {
  return (
    <>
      <Panel title="Permissions" description="The project permission authority: catalog, roles, assignments and snapshots.">
        <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-4">
          <StatTile Icon={ShieldCheckIcon} iconBg={tint.violet.bg} iconColor={tint.violet.text} label="Permissions" value="18" hint="Declared" />
          <StatTile Icon={UserCogIcon} iconBg={tint.indigo.bg} iconColor={tint.indigo.text} label="Roles" value="5" hint="2 system · 3 custom" />
          <StatTile Icon={ShieldCheckIcon} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Snapshots" value="Cached" hint="60s TTL" />
          <StatTile Icon={ShieldCheckIcon} iconBg={tint.red.bg} iconColor={tint.red.text} label="Default" value="Deny" hint="Fail-closed" />
        </div>
      </Panel>

      <Panel
        title="Declaration & snapshots"
        description="How permissions enter Nexus and how they're served."
        action={<Button size="sm">Sync declarations</Button>}
      >
        <div className="flex flex-col gap-3 text-sm">
          <div className="flex items-center gap-2">
            <span className="w-28 shrink-0 text-xs text-muted-foreground">Sources</span>
            <div className="flex flex-wrap gap-1">
              <StatusBadge tone="blue">CODE</StatusBadge>
              <StatusBadge tone="amber">YAML</StatusBadge>
              <StatusBadge tone="cyan">OPENAPI</StatusBadge>
            </div>
          </div>
          <Row label="Snapshot cache" value={<MonoChip>60s TTL · authz_version keyed</MonoChip>} />
          <Row label="Matching" value="Exact · namespace wildcard · global *" />
          <Row label="Default" value="Deny — no negative permissions in MVP" />
        </div>
      </Panel>
    </>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center gap-3">
      <span className="w-28 shrink-0 text-xs text-muted-foreground">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  );
}
