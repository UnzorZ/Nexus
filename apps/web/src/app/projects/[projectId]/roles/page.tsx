"use client";

import { useEffect, useState } from "react";
import { PlusIcon } from "@/components/ui/plus";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { DeleteIcon } from "@/components/ui/delete";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { XIcon } from "@/components/ui/x";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
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
import { Skeleton } from "@/components/ui/skeleton";
import { Stagger } from "@/components/dashboard/anim";
import {
  EmptyState,
  PageHeader,
  Panel,
  StatusBadge,
} from "@/components/dashboard/shared";
import { useProjectRoles } from "@/features/roles/useProjectRoles";
import { isValidPermissionKey, type Role } from "@/features/roles/api";
import { useProject } from "../useProject";

function RolesLoading() {
  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Stagger className="mt-6">
        <Panel title="Roles">
          <div className="flex flex-col gap-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        </Panel>
      </Stagger>
    </Stagger>
  );
}

function sameSet(a: string[], b: string[]): boolean {
  if (a.length !== b.length) return false;
  const sb = new Set(b);
  return a.every((k) => sb.has(k));
}

export default function ProjectRolesPage() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const {
    roles,
    loading,
    error,
    actionError,
    fieldErrors,
    busy,
    create,
    update,
    remove,
    setPermissions,
    refresh,
  } = useProjectRoles(project?.id ?? "");

  const canManage = project?.canManage ?? false;
  const name = project?.name ?? "...";

  // Create-role dialog
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState({
    key: "",
    label: "",
    description: "",
  });

  // Edit-role dialog (label/description + permission keys)
  const [editing, setEditing] = useState<Role | null>(null);
  const [editLabel, setEditLabel] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [keys, setKeys] = useState<string[]>([]);
  const [newKey, setNewKey] = useState("");
  const [keyError, setKeyError] = useState<string | null>(null);

  const [removeTarget, setRemoveTarget] = useState<Role | null>(null);

  useEffect(() => {
    if (editing) {
      setEditLabel(editing.label);
      setEditDescription(editing.description ?? "");
      setKeys(editing.permissionKeys);
      setNewKey("");
      setKeyError(null);
    }
  }, [editing]);

  const loadingState = projectLoading || (Boolean(project) && loading);

  function addKey() {
    const trimmed = newKey.trim();
    if (!trimmed) return;
    if (!isValidPermissionKey(trimmed)) {
      setKeyError("Use lowercase, dot-separated keys (e.g. orders.cancel, orders.*, *).");
      return;
    }
    if (keys.includes(trimmed)) {
      setNewKey("");
      setKeyError(null);
      return;
    }
    setKeys((current) => [...current, trimmed]);
    setNewKey("");
    setKeyError(null);
  }

  async function onCreate() {
    const body = {
      key: createForm.key.trim(),
      label: createForm.label.trim(),
      description: createForm.description.trim()
        ? createForm.description.trim()
        : null,
    };
    if (!body.key || !body.label) return;
    const ok = await create(body);
    if (ok) {
      setCreateForm({ key: "", label: "", description: "" });
      setCreateOpen(false);
    }
  }

  async function onEditSave() {
    if (!editing) return;
    const label = editLabel.trim();
    const description = editDescription.trim() ? editDescription.trim() : null;
    if (!label) return;

    const detailsChanged =
      label !== editing.label || description !== (editing.description ?? null);
    const keysChanged = !sameSet(keys, editing.permissionKeys);

    if (detailsChanged) {
      const ok = await update(editing.id, { label, description });
      if (!ok) return;
    }
    if (keysChanged) {
      const ok = await setPermissions(editing.id, keys);
      if (!ok) return;
    }
    setEditing(null);
  }

  if (loadingState) {
    return <RolesLoading />;
  }

  if (projectError || error) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", name, "Roles"]}
          title="Roles"
          description=""
          projectId={project?.id}
        />
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              title="Could not load roles"
              description={projectError ?? error ?? "Unknown error"}
              action={
                <Button variant="outline" onClick={() => refresh()}>
                  Retry
                </Button>
              }
            />
          </Panel>
        </Stagger>
      </Stagger>
    );
  }

  if (!project || !roles) {
    return null;
  }

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, "Roles"]}
        title="Roles"
        description={`Group permission keys into roles. In the identity milestone, end users will be assigned these roles.`}
        projectId={project.id}
        badge={
          <StatusBadge tone="emerald" dot pulse>
            {roles.length} roles
          </StatusBadge>
        }
        actions={
          canManage ? (
            <Button onClick={() => setCreateOpen(true)}>
              <PlusIcon size={14} />
              New role
            </Button>
          ) : undefined
        }
      />

      {actionError ? (
        <p className="mt-4 text-sm text-destructive">{actionError}</p>
      ) : null}

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Project roles"
          description="Each role bundles the permission keys granted to anyone holding it."
        >
          {roles.length === 0 ? (
            <EmptyState
              Icon={ShieldCheckIcon}
              title="No roles yet"
              description="Create a role and assign it permission keys from your catalog."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Role</TableHead>
                  <TableHead>Permissions</TableHead>
                  {canManage ? <TableHead className="w-8" /> : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {roles.map((role) => (
                  <TableRow key={role.id}>
                    <TableCell>
                      <div className="flex flex-col">
                        <span className="flex items-center gap-1.5 font-medium text-foreground">
                          {role.label}
                          {role.system ? (
                            <StatusBadge tone="slate">system</StatusBadge>
                          ) : null}
                        </span>
                        <code className="font-mono text-[11px] text-muted-foreground">
                          {role.key}
                        </code>
                      </div>
                    </TableCell>
                    <TableCell>
                      <StatusBadge tone="violet">
                        {role.permissionKeys.length}
                      </StatusBadge>
                    </TableCell>
                    {canManage ? (
                      <TableCell>
                        <DropdownMenu modal={false}>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              aria-label={`Actions for ${role.label}`}
                            >
                              <EllipsisIcon size={14} />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-44">
                            <DropdownMenuItem
                              disabled={busy}
                              onClick={() => setEditing(role)}
                            >
                              Edit &amp; permissions
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              variant="destructive"
                              disabled={busy}
                              onClick={() => setRemoveTarget(role)}
                              className="text-destructive focus:text-destructive"
                            >
                              <DeleteIcon className="size-3.5" />
                              Delete
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    ) : null}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Panel>
      </Stagger>

      {/* Create role */}
      <Dialog
        open={createOpen}
        onOpenChange={(open) => {
          setCreateOpen(open);
          if (!open) setCreateForm({ key: "", label: "", description: "" });
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>New role</DialogTitle>
            <DialogDescription>
              Define a role key you can assign permission keys to. The key is a
              stable slug and can&apos;t change.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="role-key">Key</Label>
              <Input
                id="role-key"
                placeholder="operator"
                value={createForm.key}
                onChange={(e) =>
                  setCreateForm((f) => ({ ...f, key: e.target.value }))
                }
                className="font-mono"
                aria-invalid={Boolean(fieldErrors.key)}
              />
              {fieldErrors.key ? (
                <p className="text-xs text-destructive">{fieldErrors.key}</p>
              ) : (
                <p className="text-[11px] text-muted-foreground">
                  Lowercase letters, numbers, hyphens, underscores.
                </p>
              )}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="role-label">Label</Label>
              <Input
                id="role-label"
                placeholder="Operator"
                value={createForm.label}
                onChange={(e) =>
                  setCreateForm((f) => ({ ...f, label: e.target.value }))
                }
                aria-invalid={Boolean(fieldErrors.label)}
              />
              {fieldErrors.label ? (
                <p className="text-xs text-destructive">{fieldErrors.label}</p>
              ) : null}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="role-desc">Description</Label>
              <Textarea
                id="role-desc"
                placeholder="What this role is for"
                value={createForm.description}
                onChange={(e) =>
                  setCreateForm((f) => ({ ...f, description: e.target.value }))
                }
                rows={2}
              />
            </div>
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              onClick={onCreate}
              disabled={busy || !createForm.key.trim() || !createForm.label.trim()}
            >
              {busy ? "Creating…" : "Create role"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Edit role + permission keys */}
      <Dialog
        open={editing !== null}
        onOpenChange={(open) => {
          if (!open) setEditing(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Edit {editing?.label}</DialogTitle>
            <DialogDescription>
              Update the label and description, and manage which permission keys
              this role grants. Wildcards like <code>orders.*</code> and{" "}
              <code>*</code> are allowed.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-label">Label</Label>
              <Input
                id="edit-label"
                value={editLabel}
                onChange={(e) => setEditLabel(e.target.value)}
                aria-invalid={Boolean(fieldErrors.label)}
              />
              {fieldErrors.label ? (
                <p className="text-xs text-destructive">{fieldErrors.label}</p>
              ) : null}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-desc">Description</Label>
              <Textarea
                id="edit-desc"
                value={editDescription}
                onChange={(e) => setEditDescription(e.target.value)}
                rows={2}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Permission keys</Label>
              <div className="flex gap-2">
                <Input
                  placeholder="orders.cancel"
                  value={newKey}
                  onChange={(e) => {
                    setNewKey(e.target.value);
                    setKeyError(null);
                  }}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault();
                      addKey();
                    }
                  }}
                  className="font-mono"
                />
                <Button variant="outline" type="button" onClick={addKey}>
                  Add
                </Button>
              </div>
              {keyError ? (
                <p className="text-xs text-destructive">{keyError}</p>
              ) : null}
              {keys.length > 0 ? (
                <div className="flex flex-wrap gap-1.5">
                  {keys.map((k) => (
                    <Badge
                      key={k}
                      variant="secondary"
                      className="gap-1 font-mono text-[11px]"
                    >
                      {k}
                      <button
                        type="button"
                        onClick={() => setKeys((c) => c.filter((x) => x !== k))}
                        className="text-muted-foreground hover:text-foreground"
                        aria-label={`Remove ${k}`}
                      >
                        <XIcon size={12} />
                      </button>
                    </Badge>
                  ))}
                </div>
              ) : (
                <p className="text-[11px] text-muted-foreground">
                  This role grants no permissions yet.
                </p>
              )}
            </div>
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button onClick={onEditSave} disabled={busy || !editLabel.trim()}>
              {busy ? "Saving…" : "Save changes"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Remove confirmation */}
      <Dialog
        open={removeTarget !== null}
        onOpenChange={(open) => {
          if (!open) setRemoveTarget(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete {removeTarget?.label}?</DialogTitle>
            <DialogDescription>
              The role and its permission grants will be removed. (End-user
              assignments arrive with the identity milestone.)
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              variant="destructive"
              disabled={busy}
              onClick={async () => {
                if (removeTarget) {
                  await remove(removeTarget.id);
                }
                setRemoveTarget(null);
              }}
            >
              Delete role
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}
