"use client";

import { useState } from "react";
import { Ban, Check, KeyRound, Pencil, Pause, Play, ShieldCheck, Trash2, UserPlus } from "lucide-react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
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
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { UsersRoundIcon } from "@/components/ui/users-round";
import { Stagger } from "@/components/dashboard/anim";
import {
  EmptyState,
  PageHeader,
  Panel,
  StatusBadge,
} from "@/components/dashboard/shared";
import {
  colorFor,
  formatLastActive,
  initials,
} from "@/features/members/display";
import {
  useCreateProjectUser,
  useDeleteProjectUser,
  useDisableProjectUser,
  useProjectUsers,
  useReactivateProjectUser,
  useResetProjectUserPassword,
  useSuspendProjectUser,
  useUpdateProjectUser,
} from "@/features/users/queries";
import { toFieldErrors, toMessage } from "@/lib/api/errors";
import type { ProjectUser, ProjectUserStatus } from "@/features/users/api";
import { useProjectRoles } from "@/features/roles/queries";
import { useUserRoles, useSetUserRoles } from "@/features/user-roles/queries";
import { useProject } from "../useProject";

function UsersLoading() {
  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Stagger className="mt-6">
        <Panel title="Users">
          <div className="flex flex-col gap-2">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        </Panel>
      </Stagger>
    </Stagger>
  );
}

const STATUS_META: Record<
  ProjectUserStatus,
  { label: string; tone: "emerald" | "amber" | "slate"; dot: boolean }
> = {
  ACTIVE: { label: "Active", tone: "emerald", dot: true },
  PENDING_VERIFICATION: { label: "Pending", tone: "amber", dot: true },
  SUSPENDED: { label: "Suspended", tone: "amber", dot: true },
  DISABLED: { label: "Disabled", tone: "slate", dot: true },
};

const USER_MESSAGES = {
  permission: "You don't have permission to manage project users.",
  notFound: "This user no longer exists.",
  codes: {
    conflict: "A user with that email already exists in this project.",
  },
};

export default function ProjectUsersPage() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const projectId = project?.id ?? "";

  const usersQ = useProjectUsers(projectId);
  const createM = useCreateProjectUser(projectId);
  const updateM = useUpdateProjectUser(projectId);
  const suspendM = useSuspendProjectUser(projectId);
  const reactivateM = useReactivateProjectUser(projectId);
  const disableM = useDisableProjectUser(projectId);
  const deleteM = useDeleteProjectUser(projectId);
  const resetM = useResetProjectUserPassword(projectId);

  const users = usersQ.data ?? null;
  const usersLoading = usersQ.isLoading;
  const usersError = usersQ.error ? toMessage(usersQ.error) : null;
  const refresh = () => usersQ.refetch();

  const [createOpen, setCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState({
    email: "",
    displayName: "",
    username: "",
    password: "",
  });
  const [editTarget, setEditTarget] = useState<ProjectUser | null>(null);
  const [editForm, setEditForm] = useState({ displayName: "", username: "" });
  const [resetTarget, setResetTarget] = useState<ProjectUser | null>(null);
  const [resetPassword, setResetPassword] = useState("");
  const [deleteTarget, setDeleteTarget] = useState<ProjectUser | null>(null);
  const [rolesTarget, setRolesTarget] = useState<ProjectUser | null>(null);
  // Desviaciones explícitas del usuario respecto al baseline (roles guardados),
  // derivado de la query. Evita sincronizar estado desde la query con un effect
  // (que dispararía una cascada de renders).
  const [roleToggles, setRoleToggles] = useState<Record<string, boolean>>({});

  const rolesQ = useProjectRoles(projectId);
  const userRolesQ = useUserRoles(projectId, rolesTarget?.id ?? "");
  const setRolesM = useSetUserRoles(projectId);
  const savedRoleIds = new Set((userRolesQ.data ?? []).map((r) => r.id));
  const roleIsSelected = (roleId: string) =>
    roleToggles[roleId] ?? savedRoleIds.has(roleId);
  const selectedRoleIds =
    rolesQ.data?.filter((r) => roleIsSelected(r.id)).map((r) => r.id) ?? [];

  const busy =
    createM.isPending ||
    updateM.isPending ||
    suspendM.isPending ||
    reactivateM.isPending ||
    disableM.isPending ||
    deleteM.isPending ||
    resetM.isPending ||
    setRolesM.isPending;

  const createFieldErrors = toFieldErrors(createM.error);
  const createError =
    createM.error && Object.keys(createFieldErrors).length === 0
      ? toMessage(createM.error, USER_MESSAGES)
      : null;
  const actionErr =
    updateM.error ??
    suspendM.error ??
    reactivateM.error ??
    disableM.error ??
    deleteM.error ??
    resetM.error ??
    setRolesM.error;
  const actionError = actionErr ? toMessage(actionErr, USER_MESSAGES) : null;

  const loading = projectLoading || (Boolean(project) && usersLoading);
  const name = project?.name ?? "...";
  const canManage = project?.canManage ?? false;

  function resetMutations() {
    updateM.reset();
    suspendM.reset();
    reactivateM.reset();
    disableM.reset();
    deleteM.reset();
    resetM.reset();
  }

  async function onCreate() {
    try {
      await createM.mutateAsync({
        email: createForm.email.trim(),
        displayName: createForm.displayName.trim(),
        username: createForm.username.trim() || null,
        password: createForm.password,
      });
      setCreateForm({ email: "", displayName: "", username: "", password: "" });
      setCreateOpen(false);
    } catch {
      /* error vía createM.error */
    }
  }

  async function onEdit() {
    if (!editTarget) return;
    resetMutations();
    try {
      await updateM.mutateAsync({
        userId: editTarget.id,
        payload: {
          displayName: editForm.displayName.trim(),
          username: editForm.username.trim() || null,
        },
      });
      setEditTarget(null);
    } catch {
      /* actionError */
    }
  }

  async function onReset() {
    if (!resetTarget) return;
    resetMutations();
    try {
      await resetM.mutateAsync({
        userId: resetTarget.id,
        newPassword: resetPassword,
      });
      setResetTarget(null);
      setResetPassword("");
    } catch {
      /* actionError */
    }
  }

  async function onDelete() {
    if (!deleteTarget) return;
    resetMutations();
    try {
      await deleteM.mutateAsync(deleteTarget.id);
      setDeleteTarget(null);
    } catch {
      /* actionError */
    }
  }

  async function onSetRoles() {
    if (!rolesTarget) return;
    if (sameIds([...savedRoleIds], selectedRoleIds)) {
      setRolesTarget(null);
      setRoleToggles({});
      return;
    }
    resetMutations();
    try {
      await setRolesM.mutateAsync({ userId: rolesTarget.id, roleIds: selectedRoleIds });
      setRolesTarget(null);
      setRoleToggles({});
    } catch {
      /* actionError */
    }
  }

  if (loading) {
    return <UsersLoading />;
  }

  if (projectError || usersError) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", name, "Project users"]}
          title="Project users"
          description=""
          projectId={project?.id}
        />
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              title="Could not load users"
              description={projectError ?? usersError ?? "Unknown error"}
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

  if (!project || !users) {
    return null;
  }

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, "Project users"]}
        title="Project users"
        description="End users of this project's OAuth/OIDC realm. Distinct from project members (Nexus accounts who manage the project)."
        projectId={project.id}
        badge={
          <StatusBadge tone="emerald" dot pulse>
            {users.length} {users.length === 1 ? "user" : "users"}
          </StatusBadge>
        }
        actions={
          canManage ? (
            <Button onClick={() => setCreateOpen(true)} disabled={busy}>
              <UserPlus size={14} />
              Create user
            </Button>
          ) : undefined
        }
      />

      {actionError ? (
        <p className="mt-4 text-sm text-destructive">{actionError}</p>
      ) : null}

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Users"
          description="Users are created active with an admin-set password. Login is available at /p/{slug}/login."
        >
          {users.length === 0 ? (
            <EmptyState
              Icon={UsersRoundIcon}
              title="No users yet"
              description="Create the first end user for this project's OAuth realm."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>User</TableHead>
                  <TableHead>Username</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Last login</TableHead>
                  <TableHead>Created</TableHead>
                  {canManage ? <TableHead className="w-8" /> : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.map((user) => {
                  const status = STATUS_META[user.status];
                  return (
                    <TableRow key={user.id}>
                      <TableCell>
                        <div className="flex items-center gap-2.5">
                          <Avatar className="size-7">
                            <AvatarFallback
                              className={`size-7 text-[10px] font-semibold text-white ${colorFor(user.email)}`}
                            >
                              {initials(user.displayName)}
                            </AvatarFallback>
                          </Avatar>
                          <div className="flex flex-col">
                            <span className="font-medium text-foreground">
                              {user.displayName}
                            </span>
                            <span className="text-[11px] text-muted-foreground">
                              {user.email}
                            </span>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {user.username ? (
                          <span className="font-mono text-xs">{user.username}</span>
                        ) : (
                          <span className="text-xs">—</span>
                        )}
                      </TableCell>
                      <TableCell>
                        <StatusBadge tone={status.tone} dot={status.dot}>
                          {status.label}
                        </StatusBadge>
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatLastActive(user.lastLoginAt)}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatLastActive(user.createdAt)}
                      </TableCell>
                      {canManage ? (
                        <TableCell>
                          <DropdownMenu modal={false}>
                            <DropdownMenuTrigger asChild>
                              <Button
                                variant="ghost"
                                size="icon-sm"
                                aria-label={`Actions for ${user.displayName}`}
                              >
                                <EllipsisIcon size={14} />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end" className="w-44">
                              <DropdownMenuItem
                                disabled={busy}
                                onClick={() => {
                                  setEditTarget(user);
                                  setEditForm({
                                    displayName: user.displayName,
                                    username: user.username ?? "",
                                  });
                                }}
                              >
                                <Pencil className="size-3.5" />
                                Edit profile
                              </DropdownMenuItem>
                              <DropdownMenuItem
                                disabled={busy}
                                onClick={() => setRolesTarget(user)}
                              >
                                <ShieldCheck className="size-3.5" />
                                Assign roles
                              </DropdownMenuItem>
                              {user.status === "SUSPENDED" ? (
                                <DropdownMenuItem
                                  disabled={busy}
                                  onClick={() => {
                                    resetMutations();
                                    reactivateM.mutate(user.id);
                                  }}
                                >
                                  <Play className="size-3.5" />
                                  Reactivate
                                </DropdownMenuItem>
                              ) : (
                                <DropdownMenuItem
                                  disabled={busy || user.status === "DISABLED"}
                                  onClick={() => {
                                    resetMutations();
                                    suspendM.mutate(user.id);
                                  }}
                                >
                                  <Pause className="size-3.5" />
                                  Suspend
                                </DropdownMenuItem>
                              )}
                              <DropdownMenuItem
                                disabled={busy || user.status === "DISABLED"}
                                onClick={() => {
                                  resetMutations();
                                  disableM.mutate(user.id);
                                }}
                              >
                                <Ban className="size-3.5" />
                                Disable
                              </DropdownMenuItem>
                              <DropdownMenuSeparator />
                              <DropdownMenuItem
                                disabled={busy}
                                onClick={() => {
                                  resetMutations();
                                  setResetTarget(user);
                                  setResetPassword("");
                                }}
                              >
                                <KeyRound className="size-3.5" />
                                Reset password
                              </DropdownMenuItem>
                              <DropdownMenuItem
                                variant="destructive"
                                disabled={busy}
                                onClick={() => setDeleteTarget(user)}
                                className="text-destructive focus:text-destructive"
                              >
                                <Trash2 className="size-3.5" />
                                Delete
                              </DropdownMenuItem>
                            </DropdownMenuContent>
                          </DropdownMenu>
                        </TableCell>
                      ) : null}
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </Panel>
      </Stagger>

      {/* Create dialog */}
      <Dialog
        open={createOpen}
        onOpenChange={(open) => {
          setCreateOpen(open);
          if (!open) {
            setCreateForm({ email: "", displayName: "", username: "", password: "" });
            createM.reset();
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create project user</DialogTitle>
            <DialogDescription>
              The user is created active with the password you set. They can sign
              in at /p/{project.slug ?? "{slug}"}/login.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <Field
              id="cu-email"
              label="Email"
              type="email"
              value={createForm.email}
              onChange={(v) => setCreateForm((f) => ({ ...f, email: v }))}
              error={createFieldErrors.email}
            />
            <Field
              id="cu-display"
              label="Display name"
              value={createForm.displayName}
              onChange={(v) => setCreateForm((f) => ({ ...f, displayName: v }))}
              error={createFieldErrors.displayName}
            />
            <Field
              id="cu-username"
              label="Username (optional)"
              value={createForm.username}
              onChange={(v) => setCreateForm((f) => ({ ...f, username: v }))}
              error={createFieldErrors.username}
            />
            <Field
              id="cu-password"
              label="Password"
              type="password"
              value={createForm.password}
              onChange={(v) => setCreateForm((f) => ({ ...f, password: v }))}
              error={createFieldErrors.password}
            />
            {createError ? (
              <p className="text-xs text-destructive">{createError}</p>
            ) : null}
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              onClick={onCreate}
              disabled={
                busy ||
                !createForm.email.trim() ||
                !createForm.displayName.trim() ||
                createForm.password.length < 8
              }
            >
              {busy ? "Creating…" : "Create user"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Edit profile dialog */}
      <Dialog
        open={editTarget !== null}
        onOpenChange={(open) => {
          if (!open) setEditTarget(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Edit {editTarget?.displayName}</DialogTitle>
            <DialogDescription>
              Update the display name and username. This does not change status or
              password.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <Field
              id="ed-display"
              label="Display name"
              value={editForm.displayName}
              onChange={(v) => setEditForm((f) => ({ ...f, displayName: v }))}
            />
            <Field
              id="ed-username"
              label="Username (optional)"
              value={editForm.username}
              onChange={(v) => setEditForm((f) => ({ ...f, username: v }))}
            />
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              onClick={onEdit}
              disabled={busy || !editForm.displayName.trim()}
            >
              {busy ? "Saving…" : "Save changes"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reset password dialog */}
      <Dialog
        open={resetTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setResetTarget(null);
            setResetPassword("");
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reset password for {resetTarget?.displayName}?</DialogTitle>
            <DialogDescription>
              Set a new password. The user must use it on their next sign-in.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2 py-1">
            <Input
              type="password"
              placeholder="New password (min 8 chars)"
              value={resetPassword}
              onChange={(e) => setResetPassword(e.target.value)}
            />
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              onClick={onReset}
              disabled={busy || resetPassword.length < 8}
            >
              {busy ? "Resetting…" : "Reset password"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete confirmation */}
      <Dialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete {deleteTarget?.displayName}?</DialogTitle>
            <DialogDescription>
              {deleteTarget?.displayName} ({deleteTarget?.email}) will be
              permanently removed from this project. This cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button variant="destructive" disabled={busy} onClick={onDelete}>
              {busy ? "Deleting…" : "Delete user"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Assign roles dialog */}
      <Dialog
        open={rolesTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setRolesTarget(null);
            setRoleToggles({});
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Assign roles to {rolesTarget?.displayName}</DialogTitle>
            <DialogDescription>
              Pick the roles this user holds. Their effective permissions are the
              union of the permissions granted to these roles.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-1 py-1">
            {rolesQ.isLoading ? (
              <Skeleton className="h-9 w-full" />
            ) : !rolesQ.data || rolesQ.data.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                This project has no roles yet. Create one under Roles first.
              </p>
            ) : (
              rolesQ.data.map((role) => {
                const selected = roleIsSelected(role.id);
                return (
                  <button
                    key={role.id}
                    type="button"
                    aria-pressed={selected}
                    onClick={() =>
                      setRoleToggles((t) => ({
                        ...t,
                        [role.id]: !roleIsSelected(role.id),
                      }))
                    }
                    className={`flex items-center justify-between rounded-md border px-3 py-2 text-left text-sm transition-colors ${
                      selected
                        ? "border-primary bg-primary/5"
                        : "border-border hover:bg-accent"
                    }`}
                  >
                    <span className="flex flex-col">
                      <span className="font-medium text-foreground">{role.label}</span>
                      <span className="font-mono text-[11px] text-muted-foreground">
                        {role.key}
                      </span>
                    </span>
                    {selected ? <Check className="size-4 text-primary" /> : null}
                  </button>
                );
              })
            )}
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button onClick={onSetRoles} disabled={busy || userRolesQ.isLoading}>
              {busy ? "Saving…" : "Save roles"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}

function sameIds(a: string[], b: string[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((id) => b.includes(id));
}

function Field({
  id,
  label,
  value,
  onChange,
  type = "text",
  error,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  error?: string;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={Boolean(error)}
      />
      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </div>
  );
}
