"use client";

import { useState } from "react";
import { Server } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { ActivityIcon } from "@/components/ui/activity";
import { ClockIcon } from "@/components/ui/clock";
import { ConnectIcon } from "@/components/ui/connect";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { GaugeIcon } from "@/components/ui/gauge";
import { Stagger, tint } from "@/components/dashboard/anim";
import {
  EmptyState,
  MonoChip,
  PageHeader,
  Panel,
  StatTile,
  StatusBadge,
  type Tone,
} from "@/components/dashboard/shared";

type HbStatus = "online" | "stale" | "offline";

type Instance = {
  id: string;
  instanceId: string;
  app: string;
  version: string;
  keyPrefix: string;
  status: HbStatus;
  lastSeen: string;
  region: string;
  runtime: string;
};

const statusMeta: Record<HbStatus, { label: string; tone: Tone; dot?: boolean; pulse?: boolean }> = {
  online: { label: "Online", tone: "emerald", dot: true, pulse: true },
  stale: { label: "Stale", tone: "amber", dot: true },
  offline: { label: "Offline", tone: "red", dot: true },
};

const instances: Instance[] = [
  { id: "i-1", instanceId: "demo-api-prod-01", app: "demo-api", version: "1.4.2", keyPrefix: "nxs_demo_••••a1b2", status: "online", lastSeen: "42 seconds ago", region: "eu-west-1", runtime: "JVM 21 · Spring 3.4" },
  { id: "i-2", instanceId: "demo-worker-01", app: "demo-worker", version: "0.9.1", keyPrefix: "nxs_demo_••••7c9d", status: "stale", lastSeen: "4 minutes ago", region: "us-east-1", runtime: "Node 22" },
  { id: "i-3", instanceId: "demo-api-stage-01", app: "demo-api", version: "1.4.0", keyPrefix: "nxs_demo_••••7c9d", status: "offline", lastSeen: "3 hours ago", region: "eu-west-1", runtime: "JVM 21 · Spring 3.4" },
  { id: "i-4", instanceId: "demo-api-prod-02", app: "demo-api", version: "1.4.2", keyPrefix: "nxs_demo_••••a1b2", status: "offline", lastSeen: "2 days ago", region: "eu-west-1", runtime: "JVM 21 · Spring 3.4" },
];

export default function HeartbeatPage() {
  const [timeout, setTimeout_] = useState("90");

  const online = instances.filter((i) => i.status === "online").length;
  const stale = instances.filter((i) => i.status === "stale").length;
  const offline = instances.filter((i) => i.status === "offline").length;

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", "Unknown project", "Heartbeat"]}
        title="Heartbeat"
        description="App instances reporting liveness to Nexus. Status is derived from last seen and the liveness timeout — not pushed by clients."
        badge={<StatusBadge tone="emerald" dot pulse>{online} online</StatusBadge>}
        actions={
          <>
            <Button variant="outline">Registry docs</Button>
            <Button>Register instance</Button>
          </>
        }
      />

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Instances"
          description="Each heartbeat is tied to the API key that reported it."
          action={
            <div className="flex items-center gap-2">
              <span className="text-[11px] text-muted-foreground">Liveness timeout</span>
              <Select value={timeout} onValueChange={setTimeout_}>
                <SelectTrigger size="sm" className="w-28">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="45">45s</SelectItem>
                  <SelectItem value="90">90s</SelectItem>
                  <SelectItem value="300">5m</SelectItem>
                </SelectContent>
              </Select>
            </div>
          }
        >
          <div className="mb-4 grid grid-cols-2 divide-x divide-border md:grid-cols-4">
            <StatTile Icon={ActivityIcon} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Online" value={online} hint="Within timeout" />
            <StatTile Icon={ClockIcon} iconBg={tint.amber.bg} iconColor={tint.amber.text} label="Stale" value={stale} hint="Grace exceeded" />
            <StatTile Icon={ConnectIcon} iconBg={tint.red.bg} iconColor={tint.red.text} label="Offline" value={offline} hint="Assumed down" />
            <StatTile Icon={GaugeIcon} iconBg={tint.cyan.bg} iconColor={tint.cyan.text} label="Instances" value={instances.length} hint="Across envs" />
          </div>

          {instances.length === 0 ? (
            <EmptyState Icon={ActivityIcon} title="No instances reporting" description="Register an app instance with an API key to start sending heartbeats." />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Instance</TableHead>
                  <TableHead>App</TableHead>
                  <TableHead>API key</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Last seen</TableHead>
                  <TableHead>Region</TableHead>
                  <TableHead className="w-8" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {instances.map((inst) => {
                  const meta = statusMeta[inst.status];
                  return (
                    <TableRow key={inst.id}>
                      <TableCell>
                        <div className="flex flex-col">
                          <MonoChip>{inst.instanceId}</MonoChip>
                          <span className="mt-0.5 text-[11px] text-muted-foreground">{inst.runtime}</span>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-col">
                          <span className="font-medium text-foreground">{inst.app}</span>
                          <span className="text-[11px] text-muted-foreground">v{inst.version}</span>
                        </div>
                      </TableCell>
                      <TableCell><MonoChip>{inst.keyPrefix}</MonoChip></TableCell>
                      <TableCell>
                        <StatusBadge tone={meta.tone} dot={meta.dot} pulse={meta.pulse}>
                          {meta.label}
                        </StatusBadge>
                      </TableCell>
                      <TableCell>
                        <span className="flex items-center gap-1.5 text-muted-foreground">
                          {inst.status === "online" ? (
                            <span className="nexus-live relative h-1.5 w-1.5 rounded-full bg-emerald-500 text-emerald-500" />
                          ) : null}
                          {inst.lastSeen}
                        </span>
                      </TableCell>
                      <TableCell>
                        <span className="flex items-center gap-1.5 text-muted-foreground">
                          <Server size={13} />
                          {inst.region}
                        </span>
                      </TableCell>
                      <TableCell>
                        <DropdownMenu modal={false}>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon-sm" aria-label={`Actions for ${inst.instanceId}`}>
                              <EllipsisIcon size={14} />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-44">
                            <DropdownMenuItem>View metadata</DropdownMenuItem>
                            <DropdownMenuItem>Ping now</DropdownMenuItem>
                            <DropdownMenuItem>View API key</DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem variant="destructive" className="text-destructive focus:text-destructive">
                              Remove stale
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}

          <div className="mt-4 flex items-center gap-2 border-t pt-3 text-[11px] text-muted-foreground">
            <ClockIcon size={14} className="shrink-0 text-emerald-500" />
            An instance is <span className="text-foreground">offline</span> after {timeout}s with no heartbeat.
            <span className="text-foreground">Stale</span> sits in the grace window before that.
          </div>
        </Panel>
      </Stagger>
    </Stagger>
  );
}
