"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
import { BookTextIcon } from "@/components/ui/book-text";
import { CodeXmlIcon } from "@/components/ui/code-xml-icon";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { FileCogIcon } from "@/components/ui/file-cog";
import { SearchIcon } from "@/components/ui/search";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { TriangleAlertIcon } from "@/components/ui/triangle-alert-icon";
import { Stagger, tint } from "@/components/dashboard/anim";
import {
  MonoChip,
  PageHeader,
  Panel,
  StatTile,
  StatusBadge,
  type Tone,
} from "@/components/dashboard/shared";

type Source = "WEB" | "YAML" | "CODE" | "OPENAPI" | "SYSTEM";

type Permission = {
  key: string;
  label: string;
  source: Source;
  enabled: boolean;
  deprecated: boolean;
  missing: boolean;
  declared: string;
};

const sourceMeta: Record<Source, { tone: Tone; Icon: React.ElementType }> = {
  SYSTEM: { tone: "violet", Icon: ShieldCheckIcon },
  CODE: { tone: "blue", Icon: CodeXmlIcon },
  YAML: { tone: "amber", Icon: FileCogIcon },
  OPENAPI: { tone: "cyan", Icon: BookTextIcon },
  WEB: { tone: "indigo", Icon: ShieldCheckIcon },
};

const initialPerms: Permission[] = [
  { key: "orders.read", label: "View orders", source: "CODE", enabled: true, deprecated: false, missing: false, declared: "2 hours ago" },
  { key: "orders.cancel", label: "Cancel orders", source: "CODE", enabled: true, deprecated: false, missing: false, declared: "2 hours ago" },
  { key: "orders.refund", label: "Refund orders", source: "CODE", enabled: true, deprecated: false, missing: false, declared: "2 hours ago" },
  { key: "products.read", label: "View products", source: "CODE", enabled: true, deprecated: false, missing: false, declared: "2 hours ago" },
  { key: "products.write", label: "Edit products", source: "CODE", enabled: true, deprecated: false, missing: false, declared: "2 hours ago" },
  { key: "products.delete", label: "Delete products", source: "CODE", enabled: false, deprecated: false, missing: false, declared: "1 day ago" },
  { key: "users.read", label: "View users", source: "CODE", enabled: true, deprecated: false, missing: false, declared: "2 hours ago" },
  { key: "users.write", label: "Edit users", source: "CODE", enabled: true, deprecated: false, missing: false, declared: "2 hours ago" },
  { key: "users.invite", label: "Invite users", source: "CODE", enabled: true, deprecated: false, missing: false, declared: "2 hours ago" },
  { key: "admin.dashboard.access", label: "Access admin dashboard", source: "YAML", enabled: true, deprecated: false, missing: false, declared: "5 days ago" },
  { key: "admin.users.invite", label: "Invite admin users", source: "YAML", enabled: true, deprecated: false, missing: false, declared: "5 days ago" },
  { key: "billing.invoices.read", label: "Read invoices", source: "OPENAPI", enabled: true, deprecated: false, missing: false, declared: "3 days ago" },
  { key: "billing.invoices.export", label: "Export invoices", source: "OPENAPI", enabled: true, deprecated: false, missing: false, declared: "3 days ago" },
  { key: "legacy.reports.run", label: "Run legacy reports", source: "WEB", enabled: true, deprecated: true, missing: false, declared: "30 days ago" },
  { key: "checkout.discounts.apply", label: "Apply checkout discounts", source: "CODE", enabled: true, deprecated: false, missing: true, declared: "9 days ago" },
];

const TOTAL_DECLARED = 18;

export default function PermissionsPage() {
  const [perms, setPerms] = useState<Permission[]>(initialPerms);
  const [query, setQuery] = useState("");
  const [sourceFilter, setSourceFilter] = useState<Source | "all">("all");

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return perms.filter((p) => {
      if (sourceFilter !== "all" && p.source !== sourceFilter) return false;
      if (!q) return true;
      return p.key.toLowerCase().includes(q) || p.label.toLowerCase().includes(q);
    });
  }, [perms, query, sourceFilter]);

  const enabled = perms.filter((p) => p.enabled && !p.deprecated).length;
  const deprecated = perms.filter((p) => p.deprecated).length;
  const missing = perms.filter((p) => p.missing).length;

  function toggleEnabled(key: string) {
    setPerms((prev) => prev.map((p) => (p.key === key ? { ...p, enabled: !p.enabled } : p)));
  }

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", "F-Shop", "Permissions"]}
        title="Permissions"
        description="Permission flags declared by F-Shop and managed by Nexus. Positive-only; effective when any assignment matches."
        badge={<StatusBadge tone="violet" dot pulse>{TOTAL_DECLARED} declared</StatusBadge>}
        actions={
          <>
            <Button variant="outline">Declaration guide</Button>
            <Button>Sync declarations</Button>
          </>
        }
      />

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Matching rules"
          description="Permission key grammar and the MVP matching algorithm."
        >
          <div className="grid gap-5 md:grid-cols-2">
            <div className="flex flex-col gap-2">
              <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                Key format
              </p>
              <div className="flex flex-wrap items-center gap-1.5">
                <MonoChip>orders.read</MonoChip>
                <MonoChip>admin.dashboard.access</MonoChip>
                <MonoChip>orders.*</MonoChip>
                <MonoChip>*</MonoChip>
              </div>
              <p className="text-[11px] leading-relaxed text-muted-foreground">
                Segments separated by dots. A trailing{" "}
                <code className="font-mono text-foreground">.*</code> matches a
                whole namespace;{" "}
                <code className="font-mono text-foreground">*</code> matches
                everything.
              </p>
            </div>
            <div className="flex flex-col gap-2">
              <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                A user is allowed if any effective permission matches
              </p>
              <ul className="flex flex-col gap-1.5 text-xs text-muted-foreground">
                <li className="flex items-center gap-2">
                  <StatusBadge tone="emerald">exact</StatusBadge>
                  <MonoChip>orders.cancel</MonoChip> matches <MonoChip>orders.cancel</MonoChip>
                </li>
                <li className="flex items-center gap-2">
                  <StatusBadge tone="blue">namespace</StatusBadge>
                  <MonoChip>orders.*</MonoChip> matches <MonoChip>orders.cancel</MonoChip>
                </li>
                <li className="flex items-center gap-2">
                  <StatusBadge tone="violet">global</StatusBadge>
                  <MonoChip>*</MonoChip> matches everything
                </li>
              </ul>
            </div>
          </div>
          <div className="mt-4 flex items-center gap-2 border-t pt-3 text-[11px] text-muted-foreground">
            <ShieldCheckIcon size={14} className="shrink-0 text-violet-500" />
            Positive-only in MVP — negative permissions are not designed yet.
          </div>
        </Panel>

        <Panel
          title="Permission catalog"
          description="Synced from app declarations. Missing entries are kept (not deleted) for audit history."
        >
          <div className="mb-4 grid grid-cols-2 divide-x divide-border md:grid-cols-4">
            <StatTile Icon={ShieldCheckIcon} iconBg={tint.violet.bg} iconColor={tint.violet.text} label="Declared" value={TOTAL_DECLARED} hint="Across sources" />
            <StatTile Icon={ShieldCheckIcon} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Enabled" value={enabled} hint={`of ${perms.length} shown`} />
            <StatTile Icon={TriangleAlertIcon} iconBg={tint.amber.bg} iconColor={tint.amber.text} label="Deprecated" value={deprecated} hint="Kept for history" />
            <StatTile Icon={FileCogIcon} iconBg={tint.red.bg} iconColor={tint.red.text} label="Missing" value={missing} hint="Not in last sync" />
          </div>

          <div className="mb-3 flex flex-wrap items-center gap-2">
            <div className="relative min-w-[220px] flex-1">
              <SearchIcon size={15} className="pointer-events-none absolute top-1/2 left-2.5 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search by key or label…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="h-8 pl-8"
              />
            </div>
            <Select value={sourceFilter} onValueChange={(v) => setSourceFilter(v as Source | "all")}>
              <SelectTrigger size="sm" className="w-36">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All sources</SelectItem>
                <SelectItem value="CODE">Code</SelectItem>
                <SelectItem value="YAML">YAML</SelectItem>
                <SelectItem value="OPENAPI">OpenAPI</SelectItem>
                <SelectItem value="WEB">Web</SelectItem>
                <SelectItem value="SYSTEM">System</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Key</TableHead>
                <TableHead>Label</TableHead>
                <TableHead>Source</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Declared</TableHead>
                <TableHead className="w-8" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((p) => {
                const src = sourceMeta[p.source];
                return (
                  <TableRow key={p.key}>
                    <TableCell><MonoChip>{p.key}</MonoChip></TableCell>
                    <TableCell className="text-foreground">{p.label}</TableCell>
                    <TableCell>
                      <StatusBadge tone={src.tone}>
                        <src.Icon size={10} />
                        {p.source}
                      </StatusBadge>
                    </TableCell>
                    <TableCell>
                      {p.missing ? (
                        <StatusBadge tone="red" dot>Missing</StatusBadge>
                      ) : p.deprecated ? (
                        <StatusBadge tone="amber">Deprecated</StatusBadge>
                      ) : p.enabled ? (
                        <StatusBadge tone="emerald" dot>Enabled</StatusBadge>
                      ) : (
                        <StatusBadge tone="slate">Disabled</StatusBadge>
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{p.declared}</TableCell>
                    <TableCell>
                      <DropdownMenu modal={false}>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon-sm" aria-label={`Actions for ${p.key}`}>
                            <EllipsisIcon size={14} />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-44">
                          <DropdownMenuItem onClick={() => toggleEnabled(p.key)}>
                            {p.enabled ? "Disable" : "Enable"}
                          </DropdownMenuItem>
                          <DropdownMenuItem>View assignees</DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem variant="destructive" className="text-destructive focus:text-destructive">
                            Deprecate
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Panel>
      </Stagger>
    </Stagger>
  );
}
