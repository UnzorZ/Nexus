"use client";

import { useMemo, useState } from "react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ActivityIcon } from "@/components/ui/activity";
import { ClipboardCheckIcon } from "@/components/ui/clipboard-check";
import { KeyCircleIcon } from "@/components/ui/key-circle";
import { SearchIcon } from "@/components/ui/search";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { TriangleAlertIcon } from "@/components/ui/triangle-alert-icon";
import { UserIcon } from "@/components/ui/user";
import { UsersRoundIcon } from "@/components/ui/users-round";
import { Stagger, tint } from "@/components/dashboard/anim";
import {
  MonoChip,
  PageHeader,
  Panel,
  StatTile,
  StatusBadge,
  type Tone,
} from "@/components/dashboard/shared";

type ActorType = "NEXUS_ACCOUNT" | "PROJECT_USER" | "API_KEY" | "SYSTEM";
type Outcome = "success" | "failure";

type AuditEvent = {
  id: string;
  actorType: ActorType;
  actor: string;
  action: string;
  resourceType: string;
  resourceId: string;
  outcome: Outcome;
  ip: string;
  time: string;
  minsAgo: number;
  traceId: string;
  userAgent: string;
  metadata: Record<string, string>;
};

const actorMeta: Record<
  ActorType,
  { tone: Tone; Icon: React.ElementType; chipBg: string; chipColor: string }
> = {
  NEXUS_ACCOUNT: { tone: "indigo", Icon: UsersRoundIcon, chipBg: tint.indigo.bg, chipColor: tint.indigo.text },
  PROJECT_USER: { tone: "violet", Icon: UserIcon, chipBg: tint.violet.bg, chipColor: tint.violet.text },
  API_KEY: { tone: "amber", Icon: KeyCircleIcon, chipBg: tint.amber.bg, chipColor: tint.amber.text },
  SYSTEM: { tone: "slate", Icon: ShieldCheckIcon, chipBg: "bg-muted", chipColor: "text-muted-foreground" },
};

const events: AuditEvent[] = [
  { id: "e-1", actorType: "API_KEY", actor: "nxs_demo_••••a1b2", action: "heartbeat.report", resourceType: "instance", resourceId: "demo-api-prod-01", outcome: "success", ip: "198.51.100.4", time: "42 seconds ago", minsAgo: 0.7, traceId: "a1b2c3d4", userAgent: "nexus-spring-boot-starter/1.0.0", metadata: { status: "online", region: "eu-west-1" } },
  { id: "e-2", actorType: "PROJECT_USER", actor: "pablo@soto.io", action: "permission.check", resourceType: "permission", resourceId: "orders.cancel", outcome: "success", ip: "203.0.113.77", time: "10 minutes ago", minsAgo: 10, traceId: "b2c3d4e5", userAgent: "demo-web/1.4.2 (Chrome)", metadata: { result: "allow", via: "role: vip" } },
  { id: "e-3", actorType: "SYSTEM", actor: "nexus", action: "permission.declare", resourceType: "permission", resourceId: "orders.refund", outcome: "success", ip: "127.0.0.1", time: "2 hours ago", minsAgo: 120, traceId: "c3d4e5f6", userAgent: "nexus-api/1.0.0", metadata: { source: "CODE", sync: "added" } },
  { id: "e-4", actorType: "SYSTEM", actor: "nexus", action: "module.gate", resourceType: "module", resourceId: "notify", outcome: "failure", ip: "127.0.0.1", time: "1 hour ago", minsAgo: 60, traceId: "d4e5f6g7", userAgent: "nexus-api/1.0.0", metadata: { reason: "module_disabled", status: "403" } },
  { id: "e-5", actorType: "PROJECT_USER", actor: "vera@lago.net", action: "auth.login", resourceType: "session", resourceId: "—", outcome: "failure", ip: "192.0.2.55", time: "6 hours ago", minsAgo: 360, traceId: "e5f6g7h8", userAgent: "Mozilla/5.0 (iPhone)", metadata: { reason: "invalid_credentials" } },
  { id: "e-6", actorType: "API_KEY", actor: "nxs_demo_••••3f0e", action: "api_key.authenticate", resourceType: "api_key", resourceId: "key-3", outcome: "failure", ip: "198.51.100.9", time: "3 days ago", minsAgo: 4320, traceId: "f6g7h8i9", userAgent: "curl/8.4.0", metadata: { reason: "disabled" } },
  { id: "e-7", actorType: "NEXUS_ACCOUNT", actor: "Marcos", action: "api_key.create", resourceType: "api_key", resourceId: "key-2", outcome: "success", ip: "203.0.113.9", time: "3 days ago", minsAgo: 4320, traceId: "g7h8i9j0", userAgent: "Mozilla/5.0 (Macintosh)", metadata: { scopes: "heartbeat:write", expires: "90d" } },
  { id: "e-8", actorType: "NEXUS_ACCOUNT", actor: "Marcos", action: "module.enable", resourceType: "module", resourceId: "identity", outcome: "success", ip: "203.0.113.9", time: "5 days ago", minsAgo: 7200, traceId: "h8i9j0k1", userAgent: "Mozilla/5.0 (Macintosh)", metadata: { from: "disabled", to: "enabled" } },
  { id: "e-9", actorType: "NEXUS_ACCOUNT", actor: "Ana", action: "member.invite", resourceType: "membership", resourceId: "sofia@example.com", outcome: "success", ip: "203.0.113.12", time: "2 days ago", minsAgo: 2880, traceId: "i9j0k1l2", userAgent: "Mozilla/5.0 (Macintosh)", metadata: { role: "ADMIN" } },
  { id: "e-10", actorType: "NEXUS_ACCOUNT", actor: "Marcos", action: "role.update", resourceType: "role", resourceId: "support-agent", outcome: "success", ip: "203.0.113.9", time: "1 day ago", minsAgo: 1440, traceId: "j0k1l2m3", userAgent: "Mozilla/5.0 (Macintosh)", metadata: { added: "orders.cancel" } },
  { id: "e-11", actorType: "NEXUS_ACCOUNT", actor: "Marcos", action: "session.revoke", resourceType: "session", resourceId: "s-9f2c", outcome: "success", ip: "203.0.113.9", time: "4 days ago", minsAgo: 5760, traceId: "k1l2m3n4", userAgent: "Mozilla/5.0 (Macintosh)", metadata: { reason: "logout_all" } },
  { id: "e-12", actorType: "NEXUS_ACCOUNT", actor: "Lucas", action: "api_key.delete", resourceType: "api_key", resourceId: "key-legacy", outcome: "success", ip: "203.0.113.21", time: "12 days ago", minsAgo: 17280, traceId: "l2m3n4o5", userAgent: "Mozilla/5.0 (Macintosh)", metadata: { rotated_to: "key-2" } },
];

const RANGE_MINS: Record<string, number> = { "24h": 1440, "7d": 10080, "30d": 43200 };

export default function AuditPage() {
  const [query, setQuery] = useState("");
  const [actorFilter, setActorFilter] = useState<ActorType | "all">("all");
  const [outcomeFilter, setOutcomeFilter] = useState<Outcome | "all">("all");
  const [range, setRange] = useState("24h");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const maxMins = RANGE_MINS[range];
    return events.filter((e) => {
      if (e.minsAgo > maxMins) return false;
      if (actorFilter !== "all" && e.actorType !== actorFilter) return false;
      if (outcomeFilter !== "all" && e.outcome !== outcomeFilter) return false;
      if (!q) return true;
      return (
        e.action.toLowerCase().includes(q) ||
        e.resourceType.toLowerCase().includes(q) ||
        e.resourceId.toLowerCase().includes(q) ||
        e.actor.toLowerCase().includes(q)
      );
    });
  }, [query, actorFilter, outcomeFilter, range]);

  const selected = events.find((e) => e.id === selectedId) ?? null;
  const failures = filtered.filter((e) => e.outcome === "failure").length;

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", "Unknown project", "Audit"]}
        title="Audit"
        description="Immutable trail of sensitive actions on this project. Metadata is intentionally small and never stores secrets, tokens, or raw JWTs."
        badge={<StatusBadge tone="amber" dot pulse>{filtered.length} events</StatusBadge>}
        actions={
          <>
            <Button variant="outline">Export NDJSON</Button>
            <Button>Retention policy</Button>
          </>
        }
      />

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Audit log"
          description="Login, key, module, permission, role and revocation events."
        >
          <div className="mb-4 grid grid-cols-2 divide-x divide-border md:grid-cols-4">
            <StatTile Icon={ClipboardCheckIcon} iconBg={tint.amber.bg} iconColor={tint.amber.text} label="Events" value={filtered.length} hint={`Last ${range}`} />
            <StatTile Icon={TriangleAlertIcon} iconBg={tint.red.bg} iconColor={tint.red.text} label="Failures" value={failures} hint="Denied / failed" />
            <StatTile Icon={ActivityIcon} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Top actor" value="SYSTEM" hint="nexus" />
            <StatTile Icon={ShieldCheckIcon} iconBg={tint.violet.bg} iconColor={tint.violet.text} label="Retention" value="90d" hint="Then archived" />
          </div>

          <div className="mb-3 flex flex-wrap items-center gap-2">
            <div className="relative min-w-[220px] flex-1">
              <SearchIcon size={15} className="pointer-events-none absolute top-1/2 left-2.5 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search action, resource or actor…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="h-8 pl-8"
              />
            </div>
            <Select value={actorFilter} onValueChange={(v) => setActorFilter(v as ActorType | "all")}>
              <SelectTrigger size="sm" className="w-40">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All actors</SelectItem>
                <SelectItem value="NEXUS_ACCOUNT">Nexus account</SelectItem>
                <SelectItem value="PROJECT_USER">Project user</SelectItem>
                <SelectItem value="API_KEY">API key</SelectItem>
                <SelectItem value="SYSTEM">System</SelectItem>
              </SelectContent>
            </Select>
            <Select value={outcomeFilter} onValueChange={(v) => setOutcomeFilter(v as Outcome | "all")}>
              <SelectTrigger size="sm" className="w-36">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All outcomes</SelectItem>
                <SelectItem value="success">Success</SelectItem>
                <SelectItem value="failure">Failure</SelectItem>
              </SelectContent>
            </Select>
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
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Actor</TableHead>
                <TableHead>Action</TableHead>
                <TableHead>Resource</TableHead>
                <TableHead>Outcome</TableHead>
                <TableHead>IP</TableHead>
                <TableHead>Time</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((event) => {
                const actor = actorMeta[event.actorType];
                return (
                  <TableRow key={event.id} onClick={() => setSelectedId(event.id)} className="cursor-pointer">
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <div className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-md ${actor.chipBg}`}>
                          <actor.Icon size={13} className={actor.chipColor} />
                        </div>
                        <div className="flex flex-col">
                          <span className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">{event.actorType}</span>
                          <span className="max-w-[160px] truncate text-xs text-foreground">{event.actor}</span>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell><MonoChip>{event.action}</MonoChip></TableCell>
                    <TableCell>
                      <span className="text-xs text-muted-foreground">
                        {event.resourceType}:<span className="text-foreground">{event.resourceId}</span>
                      </span>
                    </TableCell>
                    <TableCell>
                      {event.outcome === "success" ? (
                        <StatusBadge tone="emerald" dot>Success</StatusBadge>
                      ) : (
                        <StatusBadge tone="red" dot>Failure</StatusBadge>
                      )}
                    </TableCell>
                    <TableCell className="font-mono text-[11px] text-muted-foreground">{event.ip}</TableCell>
                    <TableCell className="text-muted-foreground">{event.time}</TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Panel>
      </Stagger>

      {/* Event detail */}
      <Sheet open={selected !== null} onOpenChange={(open) => { if (!open) setSelectedId(null); }}>
        <SheetContent className="flex w-full flex-col gap-0 sm:max-w-md">
          <SheetHeader className="gap-2">
            <SheetTitle className="flex items-center gap-2">
              <MonoChip>{selected?.action}</MonoChip>
            </SheetTitle>
            <SheetDescription>
              {selected?.resourceType}:{selected?.resourceId}
            </SheetDescription>
          </SheetHeader>

          {selected ? (
            <div className="-mx-6 flex-1 overflow-y-auto px-6">
              <dl className="grid grid-cols-2 gap-x-4 gap-y-3 border-t py-4 text-xs">
                <Detail label="Actor type" value={selected.actorType} />
                <Detail label="Actor" value={selected.actor} />
                <Detail label="Outcome" value={selected.outcome} />
                <Detail label="IP address" value={selected.ip} mono />
                <Detail label="Trace ID" value={selected.traceId} mono />
                <Detail label="Time" value={selected.time} />
              </dl>

              <div className="border-t py-4">
                <p className="mb-1 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">User agent</p>
                <p className="break-all font-mono text-[11px] text-muted-foreground">{selected.userAgent}</p>
              </div>

              <div className="border-t py-4">
                <p className="mb-2 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Metadata</p>
                <pre className="overflow-x-auto rounded-md bg-muted p-3 font-mono text-[11px] leading-relaxed text-muted-foreground">
                  {JSON.stringify(selected.metadata, null, 2)}
                </pre>
                <p className="mt-2 text-[11px] leading-relaxed text-muted-foreground">
                  Payloads are kept small on purpose — never full keys, passwords, refresh tokens or raw JWTs.
                </p>
              </div>
            </div>
          ) : null}
        </SheetContent>
      </Sheet>
    </Stagger>
  );
}

function Detail({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className={mono ? "font-mono text-foreground" : "text-foreground"}>{value}</dd>
    </div>
  );
}
