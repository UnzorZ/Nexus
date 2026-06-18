"use client";

import { HardDrive } from "lucide-react";
import { BoxIcon } from "@/components/ui/box";
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

const buckets = [
  { id: "b-1", name: "fshop-assets", visibility: "Private" as const, objects: "12 480", size: "4.2 GB", created: "Jan 12, 2026" },
  { id: "b-2", name: "fshop-uploads", visibility: "Private" as const, objects: "3 120", size: "812 MB", created: "Jan 12, 2026" },
  { id: "b-3", name: "fshop-public", visibility: "Public" as const, objects: "86", size: "54 MB", created: "Feb 1, 2026" },
  { id: "b-4", name: "fshop-temp", visibility: "Private" as const, objects: "2 041", size: "128 MB", created: "Mar 3, 2026" },
];

const lifecycleRules = [
  { id: "l-1", bucket: "fshop-temp", rule: "Delete objects after 30 days" },
  { id: "l-2", bucket: "fshop-uploads", rule: "Move to cold storage after 60 days" },
];

export function StorageModule() {
  return (
    <>
      <Panel title="Storage" description="Per-project blob storage and object lifecycle.">
        <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-4">
          <StatTile Icon={BoxIcon} iconBg={tint.blue.bg} iconColor={tint.blue.text} label="Buckets" value={buckets.length} hint="Project-scoped" />
          <StatTile Icon={HardDrive} iconBg={tint.indigo.bg} iconColor={tint.indigo.text} label="Objects" value="17.7k" hint="Across buckets" />
          <StatTile Icon={HardDrive} iconBg={tint.violet.bg} iconColor={tint.violet.text} label="Total size" value="5.2 GB" hint="Used" />
          <StatTile Icon={BoxIcon} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Public" value={buckets.filter((b) => b.visibility === "Public").length} hint="Internet-readable" />
        </div>
      </Panel>

      <Panel title="Buckets" description="Object containers isolated by project_id.">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Bucket</TableHead>
              <TableHead>Visibility</TableHead>
              <TableHead>Objects</TableHead>
              <TableHead>Size</TableHead>
              <TableHead>Created</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {buckets.map((b) => (
              <TableRow key={b.id}>
                <TableCell><MonoChip>{b.name}</MonoChip></TableCell>
                <TableCell>
                  {b.visibility === "Public" ? (
                    <StatusBadge tone="amber">Public</StatusBadge>
                  ) : (
                    <StatusBadge tone="slate">Private</StatusBadge>
                  )}
                </TableCell>
                <TableCell className="text-muted-foreground">{b.objects}</TableCell>
                <TableCell className="text-muted-foreground">{b.size}</TableCell>
                <TableCell className="text-muted-foreground">{b.created}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Panel>

      <Panel title="Lifecycle rules" description="Automatic transitions applied to objects.">
        <ul className="flex flex-col gap-1">
          {lifecycleRules.map((r) => (
            <li key={r.id} className="flex items-center gap-3 rounded-md px-2 py-1.5 hover:bg-muted/60">
              <MonoChip>{r.bucket}</MonoChip>
              <span className="text-xs text-muted-foreground">{r.rule}</span>
            </li>
          ))}
        </ul>
      </Panel>
    </>
  );
}
