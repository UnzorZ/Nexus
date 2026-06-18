"use client";

import { History, RotateCw, Database } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  MonoChip,
  Panel,
  StatTile,
  StatusBadge,
} from "@/components/dashboard/shared";
import { tint } from "@/components/dashboard/anim";

const snapshots = [
  { id: "snap-118", scope: "Full", status: "completed", size: "1.8 GB", created: "Today, 03:00", expires: "in 29 days" },
  { id: "snap-117", scope: "Full", status: "completed", size: "1.7 GB", created: "Yesterday, 03:00", expires: "in 28 days" },
  { id: "snap-116", scope: "Users", status: "completed", size: "412 MB", created: "2 days ago", expires: "in 27 days" },
  { id: "snap-115", scope: "Full", status: "failed", size: "—", created: "3 days ago", expires: "—" },
];

export function BackupModule() {
  return (
    <>
      <Panel title="Backup" description="Scheduled project snapshots and restore points.">
        <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-4">
          <StatTile Icon={Database} iconBg={tint.indigo.bg} iconColor={tint.indigo.text} label="Snapshots" value="118" hint="Kept 30 days" />
          <StatTile Icon={History} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Latest" value="Today" hint="03:00 · completed" />
          <StatTile Icon={RotateCw} iconBg={tint.cyan.bg} iconColor={tint.cyan.text} label="Schedule" value="Daily" hint="03:00 UTC" />
          <StatTile Icon={Database} iconBg={tint.violet.bg} iconColor={tint.violet.text} label="Storage" value="42 GB" hint="Snapshot store" />
        </div>
      </Panel>

      <Panel
        title="Snapshots"
        description="Point-in-time copies of project data."
        action={<Button size="sm">Create snapshot</Button>}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Snapshot</TableHead>
              <TableHead>Scope</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Size</TableHead>
              <TableHead>Created</TableHead>
              <TableHead>Expires</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {snapshots.map((s) => (
              <TableRow key={s.id}>
                <TableCell><MonoChip>{s.id}</MonoChip></TableCell>
                <TableCell>
                  <StatusBadge tone={s.scope === "Full" ? "indigo" : "cyan"}>{s.scope}</StatusBadge>
                </TableCell>
                <TableCell>
                  {s.status === "completed" ? (
                    <StatusBadge tone="emerald" dot>Completed</StatusBadge>
                  ) : (
                    <StatusBadge tone="red" dot>Failed</StatusBadge>
                  )}
                </TableCell>
                <TableCell className="text-muted-foreground">{s.size}</TableCell>
                <TableCell className="text-muted-foreground">{s.created}</TableCell>
                <TableCell className="text-muted-foreground">{s.expires}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Panel>

      <Panel title="Schedule" description="Automatic snapshot policy.">
        <div className="flex flex-col gap-2.5 text-sm">
          <Row label="Frequency" value="Daily at 03:00 UTC" />
          <Row label="Scope" value="Full project snapshot" />
          <Row label="Retention" value="30 days, then archived" />
          <Row label="Next run" value="in 19 hours" />
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
