"use client";

import { useState } from "react";
import { Flag, Settings2 } from "lucide-react";
import { FileCogIcon } from "@/components/ui/file-cog";
import { Switch } from "@/components/ui/switch";
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

type FlagDef = {
  id: string;
  key: string;
  enabled: boolean;
  type: "bool" | "number" | "string";
  value?: string;
  env: "production" | "staging";
};

const initialFlags: FlagDef[] = [
  { id: "f-1", key: "checkout.new_flow", enabled: true, type: "bool", env: "production" },
  { id: "f-2", key: "search.faceted", enabled: true, type: "bool", env: "production" },
  { id: "f-3", key: "cart.max_items", enabled: true, type: "number", value: "50", env: "production" },
  { id: "f-4", key: "beta.loyalty", enabled: false, type: "bool", env: "staging" },
];

const values = [
  { key: "brand.name", value: "Example project" },
  { key: "tax.default_rate", value: "0.21" },
  { key: "currency", value: "EUR" },
  { key: "locale", value: "es-ES" },
];

export function ConfigModule() {
  const [flags, setFlags] = useState<FlagDef[]>(initialFlags);
  const enabledCount = flags.filter((f) => f.enabled).length;

  function toggle(id: string) {
    setFlags((prev) => prev.map((f) => (f.id === id ? { ...f, enabled: !f.enabled } : f)));
  }

  return (
    <>
      <Panel title="Config" description="Dynamic project configuration and feature flags.">
        <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-4">
          <StatTile Icon={Flag} iconBg={tint.amber.bg} iconColor={tint.amber.text} label="Feature flags" value={flags.length} hint="Project-scoped" />
          <StatTile Icon={Flag} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Enabled" value={enabledCount} hint="Live now" />
          <StatTile Icon={Settings2} iconBg={tint.indigo.bg} iconColor={tint.indigo.text} label="Config values" value={values.length} hint="Key/value" />
          <StatTile Icon={FileCogIcon} iconBg={tint.violet.bg} iconColor={tint.violet.text} label="Environments" value="2" hint="prod · staging" />
        </div>
      </Panel>

      <Panel title="Feature flags" description="Toggle capabilities without redeploying.">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Flag</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Environment</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="w-10" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {flags.map((f) => (
              <TableRow key={f.id}>
                <TableCell>
                  <div className="flex flex-col">
                    <MonoChip>{f.key}</MonoChip>
                    {f.type !== "bool" && f.value ? (
                      <span className="mt-0.5 font-mono text-[11px] text-muted-foreground">{f.value}</span>
                    ) : null}
                  </div>
                </TableCell>
                <TableCell><StatusBadge tone="slate">{f.type}</StatusBadge></TableCell>
                <TableCell>
                  <StatusBadge tone={f.env === "production" ? "emerald" : "amber"}>{f.env}</StatusBadge>
                </TableCell>
                <TableCell>
                  {f.enabled ? (
                    <StatusBadge tone="emerald" dot>On</StatusBadge>
                  ) : (
                    <StatusBadge tone="slate">Off</StatusBadge>
                  )}
                </TableCell>
                <TableCell>
                  <Switch
                    size="sm"
                    checked={f.enabled}
                    onCheckedChange={() => toggle(f.id)}
                    aria-label={`Toggle ${f.key}`}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Panel>

      <Panel title="Config values" description="Static project configuration.">
        <ul className="flex flex-col gap-1">
          {values.map((v) => (
            <li key={v.key} className="flex items-center justify-between gap-3 rounded-md px-2 py-1.5 hover:bg-muted/60">
              <MonoChip>{v.key}</MonoChip>
              <span className="font-mono text-xs text-foreground">{v.value}</span>
            </li>
          ))}
        </ul>
      </Panel>
    </>
  );
}
