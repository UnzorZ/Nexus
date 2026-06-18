"use client";

import { useMemo, useState } from "react";
import { Ban, RotateCw, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
import { ClockIcon } from "@/components/ui/clock";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { KeyCircleIcon } from "@/components/ui/key-circle";
import { LockIcon } from "@/components/ui/lock";
import { PlusIcon } from "@/components/ui/plus";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { TriangleAlertIcon } from "@/components/ui/triangle-alert-icon";
import { Stagger, tint } from "@/components/dashboard/anim";
import {
  CopyButton,
  EmptyState,
  MonoChip,
  PageHeader,
  Panel,
  StatTile,
  StatusBadge,
  type Tone,
} from "@/components/dashboard/shared";

type KeyStatus = "active" | "disabled" | "expired" | "revoked";

type ApiKey = {
  id: string;
  name: string;
  prefix: string;
  scopes: string[];
  status: KeyStatus;
  lastUsedAt: string;
  expiresAt: string | null;
  createdBy: string;
  createdAt: string;
};

const SCOPE_CATALOG = [
  "heartbeat:write",
  "permissions:sync",
  "permissions:read",
  "audit:read",
  "users:read",
  "users:write",
];

const statusMeta: Record<
  KeyStatus,
  { label: string; tone: Tone; dot?: boolean }
> = {
  active: { label: "Active", tone: "emerald", dot: true },
  disabled: { label: "Disabled", tone: "slate" },
  expired: { label: "Expired", tone: "amber" },
  revoked: { label: "Revoked", tone: "red" },
};

const initialKeys: ApiKey[] = [
  {
    id: "key-1",
    name: "Production API key",
    prefix: "nxs_f-shop_••••a1b2",
    scopes: ["heartbeat:write", "permissions:sync", "audit:read"],
    status: "active",
    lastUsedAt: "42 seconds ago",
    expiresAt: null,
    createdBy: "Marcos",
    createdAt: "Jan 12, 2026",
  },
  {
    id: "key-2",
    name: "Staging API key",
    prefix: "nxs_f-shop_••••7c9d",
    scopes: ["heartbeat:write"],
    status: "active",
    lastUsedAt: "2 hours ago",
    expiresAt: "in 6 days",
    createdBy: "Ana",
    createdAt: "Mar 3, 2026",
  },
  {
    id: "key-3",
    name: "CI deploy key",
    prefix: "nxs_f-shop_••••3f0e",
    scopes: ["*"],
    status: "disabled",
    lastUsedAt: "3 days ago",
    expiresAt: null,
    createdBy: "ci-bot",
    createdAt: "Feb 20, 2026",
  },
  {
    id: "key-4",
    name: "Legacy web key",
    prefix: "nxs_f-shop_••••9b2a",
    scopes: ["users:read"],
    status: "expired",
    lastUsedAt: "2 months ago",
    expiresAt: "14 days ago",
    createdBy: "Marcos",
    createdAt: "Aug 1, 2025",
  },
];

function randomSecret(len = 32) {
  const chars =
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  let out = "";
  for (let i = 0; i < len; i += 1) {
    out += chars[Math.floor(Math.random() * chars.length)];
  }
  return out;
}

export default function ApiKeysPage() {
  const [keys, setKeys] = useState<ApiKey[]>(initialKeys);
  const [createOpen, setCreateOpen] = useState(false);
  const [newName, setNewName] = useState("");
  const [newScopes, setNewScopes] = useState<string[]>(["heartbeat:write"]);
  const [newExpiry, setNewExpiry] = useState("90d");
  const [createdSecret, setCreatedSecret] = useState<string | null>(null);
  const [stored, setStored] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState<ApiKey | null>(null);

  const active = keys.filter((k) => k.status === "active").length;
  const expiring = keys.filter(
    (k) => k.status === "active" && k.expiresAt && k.expiresAt.startsWith("in "),
  ).length;

  const summary = useMemo(
    () => [
      {
        Icon: KeyCircleIcon,
        iconBg: tint.indigo.bg,
        iconColor: tint.indigo.text,
        label: "Active keys",
        value: active,
        hint: "Identifying F-Shop",
      },
      {
        Icon: ClockIcon,
        iconBg: tint.amber.bg,
        iconColor: tint.amber.text,
        label: "Expiring soon",
        value: expiring,
        hint: "Within 30 days",
      },
      {
        Icon: ShieldCheckIcon,
        iconBg: tint.violet.bg,
        iconColor: tint.violet.text,
        label: "Total issued",
        value: keys.length,
        hint: "All scopes additive",
      },
      {
        Icon: LockIcon,
        iconBg: tint.red.bg,
        iconColor: tint.red.text,
        label: "Hashed",
        value: "100%",
        hint: "Never stored plain",
      },
    ],
    [active, expiring, keys.length],
  );

  function toggleScope(scope: string) {
    setNewScopes((prev) =>
      prev.includes(scope) ? prev.filter((s) => s !== scope) : [...prev, scope],
    );
  }

  function openCreate() {
    setNewName("");
    setNewScopes(["heartbeat:write"]);
    setNewExpiry("90d");
    setStored(false);
    setCreateOpen(true);
  }

  function createKey() {
    const secret = `nxs_f-shop_${randomSecret()}`;
    const created: ApiKey = {
      id: `key-${Date.now()}`,
      name: newName.trim() || "Untitled key",
      prefix: `nxs_f-shop_••••${secret.slice(-4)}`,
      scopes: newScopes.length ? newScopes : ["heartbeat:write"],
      status: "active",
      lastUsedAt: "never",
      expiresAt:
        newExpiry === "never" ? null : `in ${newExpiry === "90d" ? "90" : "365"} days`,
      createdBy: "Marcos",
      createdAt: "Just now",
    };
    setKeys((prev) => [created, ...prev]);
    setCreateOpen(false);
    setCreatedSecret(secret);
    setStored(false);
  }

  function setKeyStatus(id: string, status: KeyStatus) {
    setKeys((prev) => prev.map((k) => (k.id === id ? { ...k, status } : k)));
  }

  function removeKey(id: string) {
    setKeys((prev) => prev.filter((k) => k.id !== id));
  }

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", "F-Shop", "API keys"]}
        title="API keys"
        description="Machine credentials that identify F-Shop to Nexus. Scopes are additive; secrets are shown once and stored only as a hash."
        badge={
          <StatusBadge tone="emerald" dot pulse>
            {active} active
          </StatusBadge>
        }
        actions={
          <>
            <Button variant="outline">View rotation guide</Button>
            <Button onClick={openCreate}>
              <PlusIcon size={14} />
              Create API key
            </Button>
          </>
        }
      />

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel title="API keys" description="nxs_f-shop_•••••• format · multiple keys per project are first-class.">
          <div className="mb-4 grid grid-cols-2 divide-x divide-border md:grid-cols-4">
            {summary.map((s) => (
              <StatTile key={s.label} {...s} />
            ))}
          </div>

          {keys.length === 0 ? (
            <EmptyState
              Icon={KeyCircleIcon}
              title="No API keys yet"
              description="Create your first key to let a backend app identify as F-Shop."
              action={
                <Button className="mt-1" onClick={openCreate}>
                  <PlusIcon size={14} />
                  Create API key
                </Button>
              }
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Secret</TableHead>
                  <TableHead>Scopes</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Last used</TableHead>
                  <TableHead className="w-8" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {keys.map((key) => {
                  const meta = statusMeta[key.status];
                  return (
                    <TableRow key={key.id}>
                      <TableCell>
                        <div className="flex flex-col">
                          <span className="font-medium text-foreground">
                            {key.name}
                          </span>
                          <span className="text-[11px] text-muted-foreground">
                            {key.createdBy} · {key.createdAt}
                            {key.expiresAt ? ` · expires ${key.expiresAt}` : ""}
                          </span>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-1">
                          <MonoChip>{key.prefix}</MonoChip>
                          <CopyButton value={key.prefix} label="Copy prefix" />
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex max-w-xs flex-wrap gap-1">
                          {key.scopes.map((scope) => (
                            <StatusBadge
                              key={scope}
                              tone={scope === "*" || scope.endsWith(".*") ? "violet" : "indigo"}
                            >
                              {scope}
                            </StatusBadge>
                          ))}
                        </div>
                      </TableCell>
                      <TableCell>
                        <StatusBadge tone={meta.tone} dot={meta.dot}>
                          {meta.label}
                        </StatusBadge>
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {key.lastUsedAt}
                      </TableCell>
                      <TableCell>
                        <DropdownMenu modal={false}>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              aria-label={`Actions for ${key.name}`}
                            >
                              <EllipsisIcon size={14} />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-44">
                            <DropdownMenuItem>
                              <RotateCw className="size-3.5" />
                              Rotate key
                            </DropdownMenuItem>
                            {key.status === "active" ? (
                              <DropdownMenuItem
                                onClick={() => setKeyStatus(key.id, "disabled")}
                              >
                                <Ban className="size-3.5" />
                                Disable
                              </DropdownMenuItem>
                            ) : key.status === "disabled" ? (
                              <DropdownMenuItem
                                onClick={() => setKeyStatus(key.id, "active")}
                              >
                                <RotateCw className="size-3.5" />
                                Re-enable
                              </DropdownMenuItem>
                            ) : null}
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              variant="destructive"
                              onClick={() => setRevokeTarget(key)}
                              className="text-destructive focus:text-destructive"
                            >
                              <Trash2 className="size-3.5" />
                              Revoke
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
        </Panel>
      </Stagger>

      {/* Create key */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create API key</DialogTitle>
            <DialogDescription>
              The full secret is shown only once after creation. Store it
              securely — Nexus keeps only a hash.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="key-name">Name</Label>
              <Input
                id="key-name"
                placeholder="e.g. Production API key"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Scopes</Label>
              <div className="grid grid-cols-2 gap-2">
                {SCOPE_CATALOG.map((scope) => (
                  <label
                    key={scope}
                    className="flex cursor-pointer items-center gap-2 rounded-md px-1 py-1 text-xs"
                  >
                    <Checkbox
                      checked={newScopes.includes(scope)}
                      onCheckedChange={() => toggleScope(scope)}
                    />
                    <MonoChip>{scope}</MonoChip>
                  </label>
                ))}
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Expires</Label>
              <Select value={newExpiry} onValueChange={setNewExpiry}>
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="90d">In 90 days</SelectItem>
                  <SelectItem value="365d">In 1 year</SelectItem>
                  <SelectItem value="never">Never</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button onClick={createKey}>Create key</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* One-time secret reveal */}
      <Dialog
        open={createdSecret !== null}
        onOpenChange={(open) => {
          if (!open) {
            setCreatedSecret(null);
            setStored(false);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Save your new API key</DialogTitle>
            <DialogDescription>
              Copy it now. For security, this is the only time the full secret
              is displayed.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-3 py-1">
            <div className="flex items-center gap-1 rounded-md border border-amber-200 bg-amber-50/60 p-3 text-xs dark:border-amber-500/30 dark:bg-amber-500/10">
              <TriangleAlertIcon size={16} className="shrink-0 text-amber-600 dark:text-amber-400" />
              <span className="text-amber-800 dark:text-amber-200">
                Once you close this dialog, the secret can&apos;t be recovered —
                you&apos;ll need to rotate the key.
              </span>
            </div>
            <div className="flex items-center gap-1 rounded-md bg-muted p-3">
              <MonoChip className="flex-1">{createdSecret}</MonoChip>
              <CopyButton value={createdSecret ?? ""} label="Copy secret" />
            </div>
            <label className="flex cursor-pointer items-center gap-2 text-xs">
              <Checkbox checked={stored} onCheckedChange={(v) => setStored(!!v)} />
              I&apos;ve stored the secret securely
            </label>
          </div>
          <DialogFooter>
            <Button
              disabled={!stored}
              onClick={() => {
                setCreatedSecret(null);
                setStored(false);
              }}
            >
              Done
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Revoke confirmation */}
      <Dialog
        open={revokeTarget !== null}
        onOpenChange={(open) => {
          if (!open) setRevokeTarget(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Revoke API key?</DialogTitle>
            <DialogDescription>
              <MonoChip>{revokeTarget?.prefix}</MonoChip>{" "}
              <span>
                ({revokeTarget?.name}) will stop authenticating immediately.
                This action is recorded in the audit log.
              </span>
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              variant="destructive"
              onClick={() => {
                if (revokeTarget) removeKey(revokeTarget.id);
                setRevokeTarget(null);
              }}
            >
              Revoke key
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}
