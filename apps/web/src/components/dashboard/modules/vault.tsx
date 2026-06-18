"use client";

import { KeyRound } from "lucide-react";
import { LockIcon } from "@/components/ui/lock";
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

const secrets = [
  { id: "s-1", key: "STRIPE_SECRET_KEY", version: 3, env: "production", updated: "5 days ago" },
  { id: "s-2", key: "DATABASE_URL", version: 7, env: "production", updated: "2 hours ago" },
  { id: "s-3", key: "SMTP_PASSWORD", version: 1, env: "production", updated: "Jan 12, 2026" },
  { id: "s-4", key: "FEATURE_FLAG_TOKEN", version: 2, env: "staging", updated: "1 week ago" },
];

export function VaultModule() {
  return (
    <>
      <Panel title="Vault" description="Encrypted secrets scoped to this project. Values are never returned in plain text.">
        <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-4">
          <StatTile Icon={LockIcon} iconBg={tint.red.bg} iconColor={tint.red.text} label="Secrets" value={secrets.length} hint="Key/values" />
          <StatTile Icon={KeyRound} iconBg={tint.amber.bg} iconColor={tint.amber.text} label="Versions" value={secrets.reduce((n, s) => n + s.version, 0)} hint="Total revisions" />
          <StatTile Icon={KeyRound} iconBg={tint.violet.bg} iconColor={tint.violet.text} label="Last rotated" value="2 h ago" hint="DATABASE_URL" />
          <StatTile Icon={LockIcon} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Encryption" value="AES-256" hint="GCM" />
        </div>
      </Panel>

      <Panel
        title="Secrets"
        description="Reference by key from your app — plaintext is write-only."
        action={<Button size="sm">Add secret</Button>}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Key</TableHead>
              <TableHead>Value</TableHead>
              <TableHead>Version</TableHead>
              <TableHead>Environment</TableHead>
              <TableHead>Updated</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {secrets.map((s) => (
              <TableRow key={s.id}>
                <TableCell><MonoChip>{s.key}</MonoChip></TableCell>
                <TableCell><MonoChip>••••••••redacted</MonoChip></TableCell>
                <TableCell>
                  <span className="font-mono text-[11px] text-muted-foreground">v{s.version}</span>
                </TableCell>
                <TableCell>
                  <StatusBadge tone={s.env === "production" ? "emerald" : "amber"}>
                    {s.env}
                  </StatusBadge>
                </TableCell>
                <TableCell className="text-muted-foreground">{s.updated}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Panel>

      <Panel title="Encryption" description="How secrets are protected at rest.">
        <div className="flex flex-col gap-2.5 text-sm">
          <Row label="Algorithm" value="AES-256-GCM" />
          <Row label="Key wrapping" value="Project-scoped DEK · rotated quarterly" />
          <Row label="Master key" value={<MonoChip>mk_fshop_4c8e</MonoChip>} />
          <div className="flex items-center gap-3">
            <span className="w-28 shrink-0 text-xs text-muted-foreground">Audit</span>
            <StatusBadge tone="emerald" dot>Every read &amp; write logged</StatusBadge>
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
