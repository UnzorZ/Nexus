"use client";

import { useEffect, useState } from "react";
import { Crown, LogOut } from "lucide-react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
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
import { Skeleton } from "@/components/ui/skeleton";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { MailIcon } from "@/components/ui/mail-icon";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { UsersRoundIcon } from "@/components/ui/users-round";
import { Stagger } from "@/components/dashboard/anim";
import {
  EmptyState,
  PageHeader,
  Panel,
  StatusBadge,
} from "@/components/dashboard/shared";
import { fetchCurrentAccount } from "@/features/session/api";
import {
  colorFor,
  formatLastActive,
  initials,
  roleMeta,
  statusMeta,
} from "@/features/members/display";
import { useProjectMembers } from "@/features/members/useProjectMembers";
import type { Member, MemberRole } from "@/features/members/api";
import { useProject } from "../useProject";

function MembersLoading() {
  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Stagger className="mt-6">
        <Panel title="Members">
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

const INVITABLE_ROLES: MemberRole[] = ["ADMIN", "MEMBER"];
const CHANGEABLE_ROLES: MemberRole[] = ["ADMIN", "MEMBER"];

export default function ProjectMembersPage() {
  const { project, loading: projectLoading, error: projectError, refresh: refreshProject } = useProject();
  const {
    members,
    loading: membersLoading,
    error: membersError,
    actionError,
    inviteError,
    inviteFieldErrors,
    busy,
    invite,
    changeRole,
    remove,
    transfer,
    refresh,
  } = useProjectMembers(project?.id ?? "");

  const [currentAccountId, setCurrentAccountId] = useState<string | null>(null);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<MemberRole>("MEMBER");
  const [removeTarget, setRemoveTarget] = useState<Member | null>(null);
  const [transferTarget, setTransferTarget] = useState<Member | null>(null);
  const [confirmTransfer, setConfirmTransfer] = useState("");

  useEffect(() => {
    let active = true;
    fetchCurrentAccount()
      .then((acc) => {
        if (active) setCurrentAccountId(acc?.id ?? null);
      })
      .catch(() => {
        if (active) setCurrentAccountId(null);
      });
    return () => {
      active = false;
    };
  }, []);

  const loading = projectLoading || (Boolean(project) && membersLoading);
  const name = project?.name ?? "...";
  const canManage = project?.canManage ?? false;
  const canDelete = project?.canDelete ?? false;

  async function onInvite() {
    if (!inviteEmail.trim()) return;
    const ok = await invite({ email: inviteEmail.trim(), role: inviteRole });
    if (ok) {
      setInviteEmail("");
      setInviteRole("MEMBER");
      setInviteOpen(false);
    }
  }

  async function onTransfer() {
    if (!transferTarget) return;
    const ok = await transfer(transferTarget.id);
    setTransferTarget(null);
    setConfirmTransfer("");
    if (ok) {
      // El usuario actual deja de ser owner: refresca el proyecto para que
      // canManage/canDelete se recalculen.
      refreshProject({ silent: true });
    }
  }

  if (loading) {
    return <MembersLoading />;
  }

  if (projectError || membersError) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", name, "Members"]}
          title="Members"
          description=""
          projectId={project?.id}
        />
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              title="Could not load members"
              description={projectError ?? membersError ?? "Unknown error"}
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

  if (!project || !members) {
    return null;
  }

  const owners = members.filter((m) => m.role === "OWNER");
  const activeCount = members.length;

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, "Members"]}
        title="Members"
        description="Nexus accounts who can manage this project from the dashboard. Roles are scoped to this project, separate from instance administration."
        projectId={project.id}
        badge={
          <StatusBadge tone="emerald" dot pulse>
            {activeCount} active
          </StatusBadge>
        }
        actions={
          canManage ? (
            <Button onClick={() => setInviteOpen(true)} disabled={busy}>
              <MailIcon size={14} />
              Invite member
            </Button>
          ) : undefined
        }
      />

      {actionError ? (
        <p className="mt-4 text-sm text-destructive">{actionError}</p>
      ) : null}

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Members"
          description="A project must always retain at least one active owner."
        >
          {members.length === 0 ? (
            <EmptyState
              Icon={UsersRoundIcon}
              title="No members yet"
              description="Invite a Nexus account to help manage this project."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Member</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>MFA</TableHead>
                  <TableHead>Last active</TableHead>
                  {canManage ? <TableHead className="w-8" /> : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {members.map((member) => {
                  const role = roleMeta[member.role];
                  const status = statusMeta[member.status];
                  const isLastOwner = member.role === "OWNER" && owners.length <= 1;
                  const isYou = member.accountId === currentAccountId;
                  return (
                    <TableRow key={member.id}>
                      <TableCell>
                        <div className="flex items-center gap-2.5">
                          <Avatar className="size-7">
                            <AvatarFallback
                              className={`size-7 text-[10px] font-semibold text-white ${colorFor(member.email)}`}
                            >
                              {initials(member.displayName)}
                            </AvatarFallback>
                          </Avatar>
                          <div className="flex flex-col">
                            <span className="flex items-center gap-1.5 font-medium text-foreground">
                              {member.displayName}
                              {isYou ? (
                                <StatusBadge tone="slate">You</StatusBadge>
                              ) : null}
                            </span>
                            <span className="text-[11px] text-muted-foreground">
                              {member.email}
                            </span>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        {canManage && member.role !== "OWNER" ? (
                          <DropdownMenu modal={false}>
                            <DropdownMenuTrigger asChild>
                              <button
                                type="button"
                                className="inline-flex items-center gap-1 rounded-full outline-none"
                              >
                                <StatusBadge tone={role.tone}>{role.label}</StatusBadge>
                              </button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="start" className="w-40">
                              {CHANGEABLE_ROLES.map((r) => (
                                <DropdownMenuItem
                                  key={r}
                                  disabled={busy || r === member.role}
                                  onClick={() => changeRole(member.id, r)}
                                >
                                  {roleMeta[r].label}
                                </DropdownMenuItem>
                              ))}
                            </DropdownMenuContent>
                          </DropdownMenu>
                        ) : (
                          <StatusBadge tone={role.tone}>{role.label}</StatusBadge>
                        )}
                      </TableCell>
                      <TableCell>
                        <StatusBadge tone={status.tone} dot={status.dot}>
                          {status.label}
                        </StatusBadge>
                      </TableCell>
                      <TableCell>
                        {member.mfaEnabled ? (
                          <StatusBadge tone="emerald">On</StatusBadge>
                        ) : (
                          <span className="text-xs text-muted-foreground">Off</span>
                        )}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatLastActive(member.lastActiveAt)}
                      </TableCell>
                      {canManage ? (
                        <TableCell>
                          <DropdownMenu modal={false}>
                            <DropdownMenuTrigger asChild>
                              <Button
                                variant="ghost"
                                size="icon-sm"
                                aria-label={`Actions for ${member.displayName}`}
                              >
                                <EllipsisIcon size={14} />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end" className="w-44">
                              <DropdownMenuItem
                                disabled
                                className="opacity-60"
                              >
                                Member since {formatLastActive(member.createdAt)}
                              </DropdownMenuItem>
                              {canDelete && member.role !== "OWNER" ? (
                                <>
                                  <DropdownMenuSeparator />
                                  <DropdownMenuItem
                                    disabled={busy}
                                    onClick={() => {
                                      setTransferTarget(member);
                                      setConfirmTransfer("");
                                    }}
                                  >
                                    <Crown className="size-3.5" />
                                    Transfer ownership
                                  </DropdownMenuItem>
                                </>
                              ) : null}
                              <DropdownMenuSeparator />
                              <DropdownMenuItem
                                variant="destructive"
                                disabled={busy || isLastOwner}
                                onClick={() => setRemoveTarget(member)}
                                className="text-destructive focus:text-destructive"
                              >
                                <LogOut className="size-3.5" />
                                Remove
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

          <div className="mt-4 flex items-center gap-2 border-t pt-3 text-[11px] text-muted-foreground">
            <ShieldCheckIcon size={14} className="shrink-0 text-violet-500" />
            Project administration is a membership concern — the{" "}
            <span className="text-foreground">instanceAdmin</span> flag grants global
            instance access, not project roles.
          </div>
        </Panel>
      </Stagger>

      {/* Invite dialog */}
      <Dialog
        open={inviteOpen}
        onOpenChange={(open) => {
          setInviteOpen(open);
          if (!open) {
            setInviteEmail("");
            setInviteRole("MEMBER");
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Invite member</DialogTitle>
            <DialogDescription>
              The invitee must have a Nexus account. They&apos;ll be added to this
              project with the chosen role, active immediately.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="invite-email">Email</Label>
              <Input
                id="invite-email"
                type="email"
                placeholder="teammate@example.com"
                value={inviteEmail}
                onChange={(e) => setInviteEmail(e.target.value)}
                aria-invalid={Boolean(inviteFieldErrors.email)}
              />
              {inviteFieldErrors.email ? (
                <p className="text-xs text-destructive">{inviteFieldErrors.email}</p>
              ) : null}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Role</Label>
              <Select
                value={inviteRole}
                onValueChange={(v) => setInviteRole(v as MemberRole)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {INVITABLE_ROLES.map((r) => (
                    <SelectItem key={r} value={r}>
                      {roleMeta[r].label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-[11px] text-muted-foreground">
                Owner can be granted later via role change.
              </p>
            </div>
            {inviteError ? (
              <p className="text-xs text-destructive">{inviteError}</p>
            ) : null}
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button onClick={onInvite} disabled={busy || !inviteEmail.trim()}>
              {busy ? "Adding…" : "Add member"}
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
            <DialogTitle>Remove {removeTarget?.displayName}?</DialogTitle>
            <DialogDescription>
              {removeTarget?.displayName} ({removeTarget?.email}) will lose access to
              this project immediately. They can be re-invited later.
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
              Remove member
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Transfer ownership confirmation */}
      <Dialog
        open={transferTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setTransferTarget(null);
            setConfirmTransfer("");
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              Transfer ownership to {transferTarget?.displayName}?
            </DialogTitle>
            <DialogDescription>
              {transferTarget?.displayName} ({transferTarget?.email}) will become
              the owner of this project and you&apos;ll become an Admin. The new
              owner can transfer it back. Type their email to confirm.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2 py-1">
            <Input
              placeholder={transferTarget?.email}
              value={confirmTransfer}
              onChange={(e) => setConfirmTransfer(e.target.value)}
              className="font-mono"
            />
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              variant="destructive"
              disabled={
                busy ||
                confirmTransfer.trim().toLowerCase() !==
                  (transferTarget?.email ?? "").toLowerCase()
              }
              onClick={onTransfer}
            >
              <Crown size={14} />
              {busy ? "Transferring…" : "Transfer ownership"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}
