"use client";

import { useEffect, useState } from "react";
import { PlusIcon } from "@/components/ui/plus";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { DeleteIcon } from "@/components/ui/delete";
import { KeyCircleIcon } from "@/components/ui/key-circle";
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
import { useProjectPermissions } from "@/features/permissions/useProjectPermissions";
import type { Permission } from "@/features/permissions/api";
import { useProject } from "../useProject";

function PermissionsLoading() {
  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Stagger className="mt-6">
        <Panel title="Permissions">
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

type FormState = { key: string; label: string; description: string };

const EMPTY_FORM: FormState = { key: "", label: "", description: "" };

export default function ProjectPermissionsPage() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const {
    permissions,
    loading,
    error,
    actionError,
    fieldErrors,
    busy,
    create,
    update,
    remove,
    refresh,
  } = useProjectPermissions(project?.id ?? "");

  const canManage = project?.canManage ?? false;
  const name = project?.name ?? "...";
  const [editing, setEditing] = useState<Permission | null | "new">(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [removeTarget, setRemoveTarget] = useState<Permission | null>(null);

  useEffect(() => {
    if (editing === "new") {
      setForm(EMPTY_FORM);
    } else if (editing) {
      setForm({
        key: editing.key,
        label: editing.label,
        description: editing.description ?? "",
      });
    }
  }, [editing]);

  const loadingState = projectLoading || (Boolean(project) && loading);

  function openCreate() {
    setEditing("new");
  }

  function openEdit(permission: Permission) {
    setEditing(permission);
  }

  async function onSubmit() {
    const body = {
      key: form.key.trim(),
      label: form.label.trim(),
      description: form.description.trim() ? form.description.trim() : null,
    };
    if (!body.label) return;
    const ok =
      editing === "new"
        ? await create(body)
        : editing
          ? await update(editing.id, {
              label: body.label,
              description: body.description,
            })
          : false;
    if (ok) setEditing(null);
  }

  if (loadingState) {
    return <PermissionsLoading />;
  }

  if (projectError || error) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", name, "Permissions"]}
          title="Permissions"
          description=""
          projectId={project?.id}
        />
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              title="Could not load permissions"
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

  if (!project || !permissions) {
    return null;
  }

  const formOpen = editing !== null;

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, "Permissions"]}
        title="Permissions"
        description={`Declare the permission keys ${name} uses, then group them into roles. Keys are validated by format only — you define the permission space.`}
        projectId={project.id}
        badge={
          <StatusBadge tone="emerald" dot pulse>
            {permissions.length} declared
          </StatusBadge>
        }
        actions={
          canManage ? (
            <Button onClick={openCreate}>
              <PlusIcon size={14} />
              Declare permission
            </Button>
          ) : undefined
        }
      />

      {actionError ? (
        <p className="mt-4 text-sm text-destructive">{actionError}</p>
      ) : null}

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Permission catalog"
          description="These keys are the capabilities your project grants via roles (and, later, to end users)."
        >
          {permissions.length === 0 ? (
            <EmptyState
              Icon={KeyCircleIcon}
              title="No permissions declared yet"
              description="Declare the first permission key your project uses, e.g. orders.cancel."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Key</TableHead>
                  <TableHead>Label</TableHead>
                  <TableHead>Source</TableHead>
                  {canManage ? <TableHead className="w-8" /> : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {permissions.map((permission) => (
                  <TableRow key={permission.id}>
                    <TableCell>
                      <div className="flex flex-col">
                        <code className="font-mono text-xs text-foreground">
                          {permission.key}
                        </code>
                        {permission.description ? (
                          <span className="mt-0.5 text-[11px] text-muted-foreground">
                            {permission.description}
                          </span>
                        ) : null}
                      </div>
                    </TableCell>
                    <TableCell className="font-medium">
                      {permission.label}
                    </TableCell>
                    <TableCell>
                      <StatusBadge tone="violet">{permission.source}</StatusBadge>
                    </TableCell>
                    {canManage ? (
                      <TableCell>
                        <DropdownMenu modal={false}>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              aria-label={`Actions for ${permission.label}`}
                            >
                              <EllipsisIcon size={14} />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-40">
                            <DropdownMenuItem
                              disabled={busy}
                              onClick={() => openEdit(permission)}
                            >
                              Edit
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              variant="destructive"
                              disabled={busy}
                              onClick={() => setRemoveTarget(permission)}
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

      {/* Create / edit dialog */}
      <Dialog
        open={formOpen}
        onOpenChange={(open) => {
          if (!open) setEditing(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editing === "new" ? "Declare permission" : "Edit permission"}
            </DialogTitle>
            <DialogDescription>
              {editing === "new"
                ? "Declare a permission key your project uses. Lowercase, dot-separated; wildcards allowed."
                : "Update the label and description. The key can't change."}
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="perm-key">Key</Label>
              <Input
                id="perm-key"
                placeholder="orders.cancel"
                value={form.key}
                onChange={(e) => setForm((f) => ({ ...f, key: e.target.value }))}
                disabled={editing !== "new"}
                className="font-mono"
                aria-invalid={Boolean(fieldErrors.key)}
              />
              {fieldErrors.key ? (
                <p className="text-xs text-destructive">{fieldErrors.key}</p>
              ) : (
                <p className="text-[11px] text-muted-foreground">
                  e.g. orders.cancel, orders.*, inventory.stock.read
                </p>
              )}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="perm-label">Label</Label>
              <Input
                id="perm-label"
                placeholder="Cancel order"
                value={form.label}
                onChange={(e) =>
                  setForm((f) => ({ ...f, label: e.target.value }))
                }
                aria-invalid={Boolean(fieldErrors.label)}
              />
              {fieldErrors.label ? (
                <p className="text-xs text-destructive">{fieldErrors.label}</p>
              ) : null}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="perm-desc">Description</Label>
              <Textarea
                id="perm-desc"
                placeholder="What this permission allows"
                value={form.description}
                onChange={(e) =>
                  setForm((f) => ({ ...f, description: e.target.value }))
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
              onClick={onSubmit}
              disabled={
                busy ||
                !form.label.trim() ||
                (editing === "new" && !form.key.trim())
              }
            >
              {busy ? "Saving…" : editing === "new" ? "Declare" : "Save changes"}
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
              The key{" "}
              <code className="font-mono">{removeTarget?.key}</code> will be
              removed from the catalog. Roles keep their grants (they reference
              keys, not this entry) — the key can be redeclared later.
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
              Delete permission
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}
