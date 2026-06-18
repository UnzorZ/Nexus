"use client";

import { useState } from "react";
import { Activity, Gauge, Users } from "lucide-react";
import { GaugeIcon } from "@/components/ui/gauge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  MonoChip,
  Panel,
  StatTile,
  StatusBadge,
} from "@/components/dashboard/shared";
import { tint } from "@/components/dashboard/anim";

// Mock hourly volumes (last 24h) — deterministic, no randomness.
const bars = [38, 52, 44, 61, 55, 72, 68, 80, 74, 90, 84, 96, 88, 79, 83, 71, 66, 58, 63, 49, 52, 41, 47, 38];

const topEndpoints = [
  { id: "e-1", method: "GET", path: "/orders", calls: "18.4k" },
  { id: "e-2", method: "POST", path: "/orders", calls: "9.1k" },
  { id: "e-3", method: "GET", path: "/products", calls: "7.7k" },
  { id: "e-4", method: "POST", path: "/permissions/check", calls: "6.2k" },
];

export function MetricsModule() {
  const [range, setRange] = useState("24h");

  return (
    <>
      <Panel title="Metrics" description="Project-scoped usage and instrumentation.">
        <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-4">
          <StatTile Icon={Activity} iconBg={tint.cyan.bg} iconColor={tint.cyan.text} label="API calls (24h)" value="84.2k" hint="Project API" />
          <StatTile Icon={Users} iconBg={tint.indigo.bg} iconColor={tint.indigo.text} label="Active users" value="1 204" hint="Last 24h" />
          <StatTile Icon={Gauge} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Heartbeats" value="4" hint="Reporting" />
          <StatTile Icon={GaugeIcon} iconBg={tint.red.bg} iconColor={tint.red.text} label="Error rate" value="0.3%" hint="5xx · 24h" />
        </div>
      </Panel>

      <Panel
        title="API call volume"
        description="Requests per hour handled for this project."
        action={
          <Select value={range} onValueChange={setRange}>
            <SelectTrigger size="sm" className="w-28">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="24h">Last 24h</SelectItem>
              <SelectItem value="7d">Last 7 days</SelectItem>
              <SelectItem value="30d">Last 30 days</SelectItem>
            </SelectContent>
          </Select>
        }
      >
        <div className="flex h-32 items-end gap-1">
          {bars.map((h, i) => (
            <div
              key={i}
              className="flex-1 rounded-sm bg-primary/70 transition-colors hover:bg-primary"
              style={{ height: `${h}%` }}
              title={`${h}`}
            />
          ))}
        </div>
        <div className="mt-2 flex justify-between text-[11px] text-muted-foreground">
          <span>24h ago</span>
          <span>now</span>
        </div>
      </Panel>

      <Panel title="Top endpoints" description="Most-called project endpoints.">
        <ul className="flex flex-col gap-1">
          {topEndpoints.map((e) => (
            <li key={e.id} className="flex items-center justify-between gap-3 rounded-md px-2 py-1.5 hover:bg-muted/60">
              <div className="flex items-center gap-2">
                <StatusBadge tone={e.method === "GET" ? "blue" : "emerald"}>{e.method}</StatusBadge>
                <MonoChip>{e.path}</MonoChip>
              </div>
              <span className="font-mono text-xs text-muted-foreground">{e.calls}</span>
            </li>
          ))}
        </ul>
      </Panel>
    </>
  );
}
