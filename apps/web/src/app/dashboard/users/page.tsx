"use client";

import { useMemo, useState } from "react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
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
import { ClockIcon } from "@/components/ui/clock";
import { KeyCircleIcon } from "@/components/ui/key-circle";
import { SearchIcon } from "@/components/ui/search";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { UserIcon } from "@/components/ui/user";
import { Stagger, tint } from "@/components/dashboard/anim";
import {
  MonoChip,
  PageHeader,
  Panel,
  StatTile,
  StatusBadge,
  type Tone,
} from "@/components/dashboard/shared";

type UserStatus = "active" | "pending" | "suspended";

type ProjectUser = {
  id: string;
  name: string;
  email: string;
  username: string | null;
  status: UserStatus;
  verified: boolean;
  lastLogin: string;
  roles: string[];
  permissions: string[];
  authz: number;
  created: string;
};

const statusMeta: Record<UserStatus, { label: string; tone: Tone; dot?: boolean }> = {
  active: { label: "Active", tone: "emerald", dot: true },
  pending: { label: "Pending", tone: "amber" },
  suspended: { label: "Suspended", tone: "red" },
};

const AVATAR_COLORS = [
  "bg-indigo-600",
  "bg-emerald-600",
  "bg-amber-600",
  "bg-rose-600",
  "bg-cyan-600",
  "bg-violet-600",
];

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase())
    .join("");
}

function colorFor(seed: string) {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) hash = seed.charCodeAt(i) + ((hash << 5) - hash);
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

const users: ProjectUser[] = [
  { id: "u-1", name: "Elena Vidal", email: "elena@vidal.me", username: "@elena", status: "active", verified: true, lastLogin: "5 min ago", roles: ["customer"], permissions: ["orders.read"], authz: 4, created: "Apr 2, 2026" },
  { id: "u-2", name: "Pablo Soto", email: "pablo@soto.io", username: "@pablo", status: "active", verified: true, lastLogin: "3 hours ago", roles: ["customer", "vip"], permissions: ["orders.*"], authz: 7, created: "Mar 18, 2026" },
  { id: "u-3", name: "Marta Roca", email: "marta@example.com", username: "@marta", status: "active", verified: true, lastLogin: "1 day ago", roles: ["support-agent"], permissions: ["orders.cancel", "users.read"], authz: 3, created: "Feb 9, 2026" },
  { id: "u-4", name: "Vera Lago", email: "vera@lago.net", username: "@vera", status: "active", verified: true, lastLogin: "2 days ago", roles: ["admin"], permissions: ["*"], authz: 9, created: "Jan 25, 2026" },
  { id: "u-5", name: "Carla Méndez", email: "carla@mendez.es", username: "@carlam", status: "active", verified: true, lastLogin: "8 min ago", roles: ["customer"], permissions: ["orders.read"], authz: 2, created: "May 1, 2026" },
  { id: "u-6", name: "Diego Fuentes", email: "diego@fuentes.co", username: null, status: "pending", verified: false, lastLogin: "never", roles: ["customer"], permissions: ["orders.read"], authz: 1, created: "2 days ago" },
  { id: "u-7", name: "Hugo Núñez", email: "hugo@nunez.org", username: null, status: "suspended", verified: false, lastLogin: "12 days ago", roles: [], permissions: [], authz: 1, created: "Dec 14, 2025" },
  { id: "u-8", name: "Tom Alonso", email: "tom@alonso.dev", username: "@tom", status: "active", verified: true, lastLogin: "6 hours ago", roles: ["customer"], permissions: ["orders.read"], authz: 5, created: "Apr 22, 2026" },
];

const TOTAL_USERS = 248;

export default function ProjectUsersPage() {
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<UserStatus | "all">("all");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return users.filter((u) => {
      if (statusFilter !== "all" && u.status !== statusFilter) return false;
      if (!q) return true;
      return (
        u.name.toLowerCase().includes(q) ||
        u.email.toLowerCase().includes(q) ||
        (u.username?.toLowerCase().includes(q) ?? false)
      );
    });
  }, [query, statusFilter]);

  const selected = users.find((u) => u.id === selectedId) ?? null;
  const verified = users.filter((u) => u.verified).length;
  const activeRecent = users.filter((u) => u.status === "active").length;

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", "Unknown project", "Project users"]}
        title="Project users"
        description="End users in this project's identity realm. Isolated per project — the same email in another project is a different user. These are not Nexus accounts."
        badge={<StatusBadge tone="indigo" dot pulse>{TOTAL_USERS} users</StatusBadge>}
        actions={
          <>
            <Button variant="outline">Import users</Button>
            <Button>Create user</Button>
          </>
        }
      />

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Project users"
          description="Authenticated through this project's OAuth/OIDC realm."
          action={<Button variant="link" size="sm" className="h-auto px-0 text-xs">View OAuth clients</Button>}
        >
          <div className="mb-4 grid grid-cols-2 divide-x divide-border md:grid-cols-4">
            <StatTile Icon={UserIcon} iconBg={tint.indigo.bg} iconColor={tint.indigo.text} label="Total users" value={TOTAL_USERS} hint="Identity realm" />
            <StatTile Icon={ShieldCheckIcon} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Verified" value={verified} hint={`of ${users.length} shown`} />
            <StatTile Icon={KeyCircleIcon} iconBg={tint.violet.bg} iconColor={tint.violet.text} label="With roles" value={users.filter((u) => u.roles.length > 0).length} hint="Role-assigned" />
            <StatTile Icon={ClockIcon} iconBg={tint.amber.bg} iconColor={tint.amber.text} label="Active (sample)" value={activeRecent} hint="Last 30 days" />
          </div>

          {/* Toolbar */}
          <div className="mb-3 flex flex-wrap items-center gap-2">
            <div className="relative min-w-[220px] flex-1">
              <SearchIcon size={15} className="pointer-events-none absolute top-1/2 left-2.5 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search by name, email or username…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="h-8 pl-8"
              />
            </div>
            <Select value={statusFilter} onValueChange={(v) => setStatusFilter(v as UserStatus | "all")}>
              <SelectTrigger size="sm" className="w-36">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All statuses</SelectItem>
                <SelectItem value="active">Active</SelectItem>
                <SelectItem value="pending">Pending</SelectItem>
                <SelectItem value="suspended">Suspended</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>User</TableHead>
                <TableHead>Username</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Verified</TableHead>
                <TableHead>Roles</TableHead>
                <TableHead>Last login</TableHead>
                <TableHead className="text-right">Authz</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((user) => {
                const status = statusMeta[user.status];
                return (
                  <TableRow
                    key={user.id}
                    onClick={() => setSelectedId(user.id)}
                    className="cursor-pointer"
                  >
                    <TableCell>
                      <div className="flex items-center gap-2.5">
                        <Avatar className="size-7">
                          <AvatarFallback className={`size-7 text-[10px] font-semibold text-white ${colorFor(user.email)}`}>
                            {initials(user.name)}
                          </AvatarFallback>
                        </Avatar>
                        <div className="flex flex-col">
                          <span className="font-medium text-foreground">{user.name}</span>
                          <span className="text-[11px] text-muted-foreground">{user.email}</span>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      {user.username ? (
                        <MonoChip>{user.username}</MonoChip>
                      ) : (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <StatusBadge tone={status.tone} dot={status.dot}>{status.label}</StatusBadge>
                    </TableCell>
                    <TableCell>
                      {user.verified ? (
                        <ShieldCheckIcon size={15} className="text-emerald-500" />
                      ) : (
                        <span className="text-xs text-muted-foreground">Pending</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {user.roles.length ? (
                          user.roles.map((r) => (
                            <StatusBadge key={r} tone="blue">{r}</StatusBadge>
                          ))
                        ) : (
                          <span className="text-xs text-muted-foreground">—</span>
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{user.lastLogin}</TableCell>
                    <TableCell className="text-right">
                      <span className="font-mono text-[11px] text-muted-foreground">v{user.authz}</span>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>

          <div className="mt-3 flex items-center justify-between border-t pt-3 text-[11px] text-muted-foreground">
            <span>
              Showing {filtered.length} of {TOTAL_USERS} users
            </span>
            <div className="flex items-center gap-1">
              <Button variant="outline" size="sm" disabled>Prev</Button>
              <Button variant="outline" size="sm" disabled>Next</Button>
            </div>
          </div>
        </Panel>
      </Stagger>

      {/* User detail drawer */}
      <Sheet open={selected !== null} onOpenChange={(open) => { if (!open) setSelectedId(null); }}>
        <SheetContent className="flex w-full flex-col gap-0 sm:max-w-md">
          <SheetHeader className="gap-3">
            <div className="flex items-center gap-3">
              <Avatar className="size-10">
                <AvatarFallback className={`size-10 text-xs font-semibold text-white ${selected ? colorFor(selected.email) : ""}`}>
                  {selected ? initials(selected.name) : ""}
                </AvatarFallback>
              </Avatar>
              <div className="min-w-0">
                <SheetTitle className="truncate">{selected?.name}</SheetTitle>
                <SheetDescription className="truncate">{selected?.email}</SheetDescription>
              </div>
            </div>
          </SheetHeader>

          {selected ? (
            <div className="-mx-6 flex-1 overflow-y-auto px-6">
              <dl className="grid grid-cols-2 gap-x-4 gap-y-3 border-t py-4 text-xs">
                <Detail label="Username" value={selected.username ?? "—"} />
                <Detail label="Status" value={statusMeta[selected.status].label} />
                <Detail label="Email verified" value={selected.verified ? "Yes" : "Pending"} />
                <Detail label="Authz version" value={`v${selected.authz}`} mono />
                <Detail label="Last login" value={selected.lastLogin} />
                <Detail label="Created" value={selected.created} />
              </dl>

              <div className="border-t py-4">
                <p className="mb-2 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Roles</p>
                <div className="flex flex-wrap gap-1.5">
                  {selected.roles.length ? (
                    selected.roles.map((r) => <StatusBadge key={r} tone="blue">{r}</StatusBadge>)
                  ) : (
                    <span className="text-xs text-muted-foreground">No roles assigned</span>
                  )}
                </div>
              </div>

              <div className="border-t py-4">
                <p className="mb-2 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Direct permissions</p>
                <div className="flex flex-wrap gap-1.5">
                  {selected.permissions.length ? (
                    selected.permissions.map((p) => <MonoChip key={p}>{p}</MonoChip>)
                  ) : (
                    <span className="text-xs text-muted-foreground">None — relies on roles only</span>
                  )}
                </div>
                <p className="mt-2 text-[11px] leading-relaxed text-muted-foreground">
                  Effective permissions = roles ∪ direct assignments. Bumping{" "}
                  <MonoChip>authz_version</MonoChip> invalidates cached snapshots.
                </p>
              </div>
            </div>
          ) : null}

          <div className="mt-auto flex items-center gap-2 border-t pt-4">
            <Button variant="outline" className="flex-1">Edit permissions</Button>
            <Button variant="destructive">Suspend</Button>
          </div>
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
