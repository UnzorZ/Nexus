"use client";

import { useMemo, useState } from "react";
import { Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { LockIcon } from "@/components/ui/lock";
import { PlusIcon } from "@/components/ui/plus";
import { SearchIcon } from "@/components/ui/search";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { UserCogIcon } from "@/components/ui/user-cog-icon";
import { UsersRoundIcon } from "@/components/ui/users-round";
import { Stagger, tint } from "@/components/dashboard/anim";
import {
  EmptyState,
  MonoChip,
  PageHeader,
  Panel,
  StatTile,
  StatusBadge,
} from "@/components/dashboard/shared";

type Role = {
  key: string;
  label: string;
  description: string;
  system: boolean;
  perms: string[];
  users: number;
};

const PERM_POOL: { key: string; label: string; wildcard?: boolean }[] = [
  { key: "*", label: "Global wildcard", wildcard: true },
  { key: "orders.*", label: "All order actions", wildcard: true },
  { key: "orders.read", label: "View orders" },
  { key: "orders.cancel", label: "Cancel orders" },
  { key: "orders.refund", label: "Refund orders" },
  { key: "products.read", label: "View products" },
  { key: "products.write", label: "Edit products" },
  { key: "products.delete", label: "Delete products" },
  { key: "users.read", label: "View users" },
  { key: "users.write", label: "Edit users" },
  { key: "admin.dashboard.access", label: "Admin dashboard" },
  { key: "billing.invoices.export", label: "Export invoices" },
];

const initialRoles: Role[] = [
  { key: "admin", label: "Admin", description: "Full project access.", system: true, perms: ["*"], users: 1 },
  { key: "customer", label: "Customer", description: "Default shopper role.", system: false, perms: ["orders.read"], users: 240 },
  { key: "support-agent", label: "Support agent", description: "Handle orders and read users.", system: false, perms: ["orders.read", "orders.cancel", "users.read"], users: 1 },
  { key: "vip", label: "VIP", description: "Trusted customer with order powers.", system: false, perms: ["orders.*"], users: 6 },
  { key: "editor", label: "Editor", description: "Manage the product catalog.", system: false, perms: ["products.read", "products.write"], users: 0 },
];

function roleIcon(key: string) {
  return key === "admin" ? ShieldCheckIcon : UserCogIcon;
}

export default function RolesPage() {
  const [roles, setRoles] = useState<Role[]>(initialRoles);
  const [selectedKey, setSelectedKey] = useState(initialRoles[0].key);
  const [permQuery, setPermQuery] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [newKey, setNewKey] = useState("");
  const [newLabel, setNewLabel] = useState("");
  const [deleteTarget, setDeleteTarget] = useState<Role | null>(null);

  const selected = roles.find((r) => r.key === selectedKey) ?? roles[0];

  const filteredPool = useMemo(() => {
    const q = permQuery.trim().toLowerCase();
    if (!q) return PERM_POOL;
    return PERM_POOL.filter(
      (p) => p.key.toLowerCase().includes(q) || p.label.toLowerCase().includes(q),
    );
  }, [permQuery]);

  const systemCount = roles.filter((r) => r.system).length;
  const usersWithRoles = roles.reduce((sum, r) => sum + r.users, 0);

  function togglePerm(roleKey: string, perm: string) {
    setRoles((prev) =>
      prev.map((r) => {
        if (r.key !== roleKey) return r;
        const has = r.perms.includes(perm);
        return {
          ...r,
          perms: has ? r.perms.filter((p) => p !== perm) : [...r.perms, perm],
        };
      }),
    );
  }

  function createRole() {
    const key = (newKey.trim() || newLabel.trim().toLowerCase().replace(/\s+/g, "-")).replace(/[^a-z0-9-]/g, "");
    if (!key || roles.some((r) => r.key === key)) return;
    const role: Role = {
      key,
      label: newLabel.trim() || key,
      description: "Custom role.",
      system: false,
      perms: [],
      users: 0,
    };
    setRoles((prev) => [...prev, role]);
    setSelectedKey(key);
    setNewKey("");
    setNewLabel("");
    setCreateOpen(false);
  }

  function deleteRole(key: string) {
    setRoles((prev) => {
      const next = prev.filter((r) => r.key !== key);
      if (selectedKey === key && next.length) setSelectedKey(next[0].key);
      return next;
    });
  }

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", "Unknown project", "Roles"]}
        title="Roles"
        description="Bundles of permission keys assigned to project users. Role permissions may use wildcards; roles and direct permissions are additive."
        badge={<StatusBadge tone="blue" dot pulse>{roles.length} roles</StatusBadge>}
        actions={
          <>
            <Button variant="outline">Assignment report</Button>
            <Button onClick={() => setCreateOpen(true)}>
              <PlusIcon size={14} />
              Create role
            </Button>
          </>
        }
      />

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel title="Roles" description="Role keys are stable; system roles can't be removed.">
          <div className="mb-4 grid grid-cols-2 divide-x divide-border md:grid-cols-4">
            <StatTile Icon={ShieldCheckIcon} iconBg={tint.blue.bg} iconColor={tint.blue.text} label="Roles" value={roles.length} hint="Project-scoped" />
            <StatTile Icon={LockIcon} iconBg={tint.violet.bg} iconColor={tint.violet.text} label="System" value={systemCount} hint="Protected" />
            <StatTile Icon={UserCogIcon} iconBg={tint.indigo.bg} iconColor={tint.indigo.text} label="Custom" value={roles.length - systemCount} hint="Editable" />
            <StatTile Icon={UsersRoundIcon} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Assignments" value={usersWithRoles} hint="Users with a role" />
          </div>

          <div className="grid gap-4 lg:grid-cols-[260px_1fr]">
            {/* Role list */}
            <ul className="flex flex-col gap-1">
              {roles.map((role) => {
                const Icon = roleIcon(role.key);
                const active = role.key === selectedKey;
                return (
                  <li key={role.key}>
                    <button
                      type="button"
                      onClick={() => setSelectedKey(role.key)}
                      className={`flex w-full items-center gap-2.5 rounded-md border px-2.5 py-2 text-left transition-colors ${
                        active
                          ? "border-primary/30 bg-primary/10"
                          : "border-transparent hover:bg-muted"
                      }`}
                    >
                      <div className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md ${active ? tint.blue.bg : "bg-muted"} ${active ? tint.blue.text : "text-muted-foreground"}`}>
                        <Icon size={15} />
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1.5">
                          <span className="truncate text-sm font-medium text-foreground">{role.label}</span>
                          {role.system ? <LockIcon size={11} className="text-muted-foreground" /> : null}
                        </div>
                        <div className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
                          <MonoChip>{role.key}</MonoChip>
                          <span>· {role.users} user{role.users === 1 ? "" : "s"}</span>
                        </div>
                      </div>
                    </button>
                  </li>
                );
              })}
            </ul>

            {/* Role detail */}
            {selected ? (
              <div className="flex flex-col rounded-lg ring-1 ring-border">
                <div className="flex items-start justify-between gap-3 border-b p-4">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <h3 className="text-sm font-semibold">{selected.label}</h3>
                      {selected.system ? (
                        <StatusBadge tone="violet">System</StatusBadge>
                      ) : (
                        <StatusBadge tone="slate">Custom</StatusBadge>
                      )}
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">{selected.description}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-1">
                    <Button variant="outline" size="sm">Save</Button>
                    {!selected.system ? (
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        aria-label={`Delete ${selected.label}`}
                        onClick={() => setDeleteTarget(selected)}
                      >
                        <Trash2 size={14} />
                      </Button>
                    ) : null}
                  </div>
                </div>

                <div className="flex items-center justify-between gap-2 px-4 pt-4">
                  <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                    Permissions ({selected.perms.length})
                  </p>
                  <div className="relative w-48">
                    <SearchIcon size={14} className="pointer-events-none absolute top-1/2 left-2 -translate-y-1/2 text-muted-foreground" />
                    <Input
                      placeholder="Filter permissions…"
                      value={permQuery}
                      onChange={(e) => setPermQuery(e.target.value)}
                      className="h-7 pl-7 text-xs"
                    />
                  </div>
                </div>

                <div className="grid max-h-[360px] grid-cols-1 gap-0.5 overflow-y-auto p-3 sm:grid-cols-2">
                  {filteredPool.map((p) => {
                    const checked = selected.perms.includes(p.key);
                    return (
                      <label
                        key={p.key}
                        className="flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 transition-colors hover:bg-muted"
                      >
                        <Checkbox
                          checked={checked}
                          onCheckedChange={() => togglePerm(selected.key, p.key)}
                        />
                        <div className="flex min-w-0 flex-1 items-center gap-1.5">
                          <MonoChip>{p.key}</MonoChip>
                          {p.wildcard ? <StatusBadge tone="violet">wildcard</StatusBadge> : null}
                        </div>
                        <span className="hidden truncate text-[11px] text-muted-foreground sm:block">
                          {p.label}
                        </span>
                      </label>
                    );
                  })}
                </div>

                <div className="mt-auto flex items-center gap-2 border-t p-3 text-[11px] text-muted-foreground">
                  <UsersRoundIcon size={14} className="shrink-0 text-emerald-500" />
                  Assigned to <span className="text-foreground">{selected.users}</span> user{selected.users === 1 ? "" : "s"} ·
                  role permissions reference keys (not IDs) so wildcards resolve.
                </div>
              </div>
            ) : (
              <EmptyState Icon={ShieldCheckIcon} title="Select a role" description="Pick a role to edit its permissions." />
            )}
          </div>
        </Panel>
      </Stagger>

      {/* Create role */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create role</DialogTitle>
            <DialogDescription>
              Custom roles can be assigned to project users and edited freely.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="role-label">Label</Label>
              <Input id="role-label" placeholder="e.g. Reviewer" value={newLabel} onChange={(e) => setNewLabel(e.target.value)} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="role-key">Key</Label>
              <Input id="role-key" placeholder="reviewer (auto from label if blank)" value={newKey} onChange={(e) => setNewKey(e.target.value)} className="font-mono" />
            </div>
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button onClick={createRole}>Create role</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete confirmation */}
      <Dialog open={deleteTarget !== null} onOpenChange={(open) => { if (!open) setDeleteTarget(null); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete role {deleteTarget?.label}?</DialogTitle>
            <DialogDescription>
              {deleteTarget?.users ?? 0} user{(deleteTarget?.users ?? 0) === 1 ? "" : "s"} currently have this role. They will lose only its permissions — direct permissions remain.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button variant="destructive" onClick={() => { if (deleteTarget) deleteRole(deleteTarget.key); setDeleteTarget(null); }}>
              Delete role
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}
