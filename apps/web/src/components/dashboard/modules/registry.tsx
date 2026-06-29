"use client";

import { Server } from "lucide-react";
import { ActivityIcon } from "@/components/ui/activity";
import {
  MonoChip,
  Panel,
  StatTile,
  StatusBadge,
} from "@/components/dashboard/shared";
import { RelatedLinks } from "@/components/dashboard/modules/ModuleShell";
import { tint } from "@/components/dashboard/anim";

export function RegistryModule() {
  return (
    <>
      <Panel title="Registry" description="App registration and heartbeat-based liveness for this project.">
        <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-4">
          <StatTile Icon={Server} iconBg={tint.cyan.bg} iconColor={tint.cyan.text} label="Instances" value="4" hint="Registered" />
          <StatTile Icon={ActivityIcon} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Online" value="1" hint="Within timeout" />
          <StatTile Icon={ActivityIcon} iconBg={tint.red.bg} iconColor={tint.red.text} label="Offline" value="2" hint="Assumed down" />
          <StatTile Icon={Server} iconBg={tint.amber.bg} iconColor={tint.amber.text} label="Timeout" value="90s" hint="Liveness window" />
        </div>
      </Panel>

      <RelatedLinks
        links={[
          { href: "/dashboard/heartbeat", label: "Heartbeat", hint: "4 instances · 1 online · 1 stale" },
        ]}
      />

      <Panel title="Liveness" description="How Nexus decides what's alive.">
        <div className="flex flex-col gap-2.5 text-sm">
          <Row label="Timeout" value={<MonoChip>90s · configurable</MonoChip>} />
          <Row label="Grace" value="Stale before offline is assumed" />
          <Row label="Tied to" value="The API key that reported the heartbeat" />
          <div className="flex items-center gap-3">
            <span className="w-28 shrink-0 text-xs text-muted-foreground">Offline</span>
            <StatusBadge tone="slate">Derived from last_seen_at</StatusBadge>
          </div>
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
