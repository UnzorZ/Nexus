"use client";

import { FileText } from "lucide-react";
import { BookTextIcon } from "@/components/ui/book-text";
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

const templates = [
  { id: "tpl-1", name: "Invoice", format: "PDF", lastRendered: "8 min ago" },
  { id: "tpl-2", name: "Packing slip", format: "PDF", lastRendered: "1 hour ago" },
  { id: "tpl-3", name: "Monthly statement", format: "XLSX", lastRendered: "3 days ago" },
  { id: "tpl-4", name: "Refund receipt", format: "PDF", lastRendered: "5 days ago" },
];

const recent = [
  { id: "r-1", name: "Invoice #10482", template: "Invoice", format: "PDF", time: "8 min ago" },
  { id: "r-2", name: "Packing slip #7781", template: "Packing slip", format: "PDF", time: "1 h ago" },
  { id: "r-3", name: "Statement 2026-05", template: "Monthly statement", format: "XLSX", time: "3 d ago" },
];

export function DocumentsModule() {
  return (
    <>
      <Panel title="Documents" description="Templated document generation and exports.">
        <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-4">
          <StatTile Icon={BookTextIcon} iconBg={tint.violet.bg} iconColor={tint.violet.text} label="Templates" value={templates.length} hint="Active" />
          <StatTile Icon={FileText} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Rendered (24h)" value="312" hint="PDF · XLSX" />
          <StatTile Icon={FileText} iconBg={tint.indigo.bg} iconColor={tint.indigo.text} label="Formats" value="3" hint="PDF · XLSX · CSV" />
          <StatTile Icon={BookTextIcon} iconBg={tint.red.bg} iconColor={tint.red.text} label="Failed (24h)" value="0" hint="Clean run" />
        </div>
      </Panel>

      <Panel title="Templates" description="Reusable layouts keyed by type.">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Template</TableHead>
              <TableHead>Format</TableHead>
              <TableHead>Last rendered</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {templates.map((t) => (
              <TableRow key={t.id}>
                <TableCell className="font-medium text-foreground">{t.name}</TableCell>
                <TableCell><StatusBadge tone="indigo">{t.format}</StatusBadge></TableCell>
                <TableCell className="text-muted-foreground">{t.lastRendered}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Panel>

      <Panel title="Recent documents" description="Latest rendered outputs.">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Document</TableHead>
              <TableHead>Template</TableHead>
              <TableHead>Format</TableHead>
              <TableHead>Rendered</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {recent.map((r) => (
              <TableRow key={r.id}>
                <TableCell><MonoChip>{r.name}</MonoChip></TableCell>
                <TableCell className="text-muted-foreground">{r.template}</TableCell>
                <TableCell><StatusBadge tone="indigo">{r.format}</StatusBadge></TableCell>
                <TableCell className="text-muted-foreground">{r.time}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Panel>
    </>
  );
}
