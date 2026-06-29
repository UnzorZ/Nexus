"use client";

import { useState } from "react";
import { LogOut } from "lucide-react";
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
import { ClockIcon } from "@/components/ui/clock";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { MailIcon } from "@/components/ui/mail-icon";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { UserCogIcon } from "@/components/ui/user-cog-icon";
import { UsersRoundIcon } from "@/components/ui/users-round";
import { Stagger, tint } from "@/components/dashboard/anim";
import {
  EmptyState,
  PageHeader,
  Panel,
  StatTile,
  StatusBadge,
  type Tone,
} from "@/components/dashboard/shared";

type Role = "OWNER" | "ADMIN" | "MEMBER";
type MemberStatus = "active" | "invited" | "suspended";

type Member = {
  id: string;
  name: string;
  email: string;
  role: Role;
  status: MemberStatus;
  mfa: boolean;
  lastActive: string;
  added: string;
  you?: boolean;
};

const roleMeta: Record<Role, { label: string; tone: Tone }> = {
  OWNER: { label: "Owner", tone: "violet" },
  ADMIN: { label: "Admin", tone: "blue" },
  MEMBER: { label: "Member", tone: "slate" },
};

const statusMeta: Record<MemberStatus, { label: string; tone: Tone; dot?: boolean }> = {
  active: { label: "Active", tone: "emerald", dot: true },
  invited: { label: "Invited", tone: "amber" },
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

const initialMembers: Member[] = [
  {
    id: "m-1",
    name: "Marcos",
    email: "marcos@unzor.xyz",
    role: "OWNER",
    status: "active",
    mfa: true,
    lastActive: "now",
    added: "Jan 12, 2026",
    you: true,
  },
  {
    id: "m-2",
    name: "Ana Pérez",
    email: "ana@example.com",
    role: "MEMBER",
    status: "active",
    mfa: true,
    lastActive: "2 hours ago",
    added: "Feb 1, 2026",
  },
  {
    id: "m-3",
    name: "Lucas Díaz",
    email: "lucas@example.com",
    role: "MEMBER",
    status: "active",
    mfa: false,
    lastActive: "yesterday",
    added: "Mar 10, 2026",
  },
  {
    id: "m-4",
    name: "Sofía Romero",
    email: "sofia@example.com",
    role: "ADMIN",
    status: "invited",
    mfa: false,
    lastActive: "—",
    added: "2 days ago",
  },
];

export default function MembersPage() {
  const [members, setMembers] = useState<Member[]>(initialMembers);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<Role>("MEMBER");
  const [removeTarget, setRemoveTarget] = useState<Member | null>(null);

  const owners = members.filter((m) => m.role === "OWNER" && m.status !== "suspended");
  const active = members.filter((m) => m.status === "active").length;
  const pending = members.filter((m) => m.status === "invited").length;

  function changeRole(id: string, role: Role) {
    setMembers((prev) => prev.map((m) => (m.id === id ? { ...m, role } : m)));
  }

  function removeMember(id: string) {
    setMembers((prev) => prev.filter((m) => m.id !== id));
  }

  function invite() {
    if (!inviteEmail.trim()) return;
    const name = inviteEmail.split("@")[0].replace(/[._-]+/g, " ");
    const member: Member = {
      id: `m-${Date.now()}`,
      name: name.charAt(0).toUpperCase() + name.slice(1),
      email: inviteEmail.trim(),
      role: inviteRole,
      status: "invited",
      mfa: false,
      lastActive: "—",
      added: "Just now",
    };
    setMembers((prev) => [...prev, member]);
    setInviteEmail("");
    setInviteRole("MEMBER");
    setInviteOpen(false);
  }

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", "Unknown project", "Members"]}
        title="Members"
        description="Nexus accounts who can manage this project from the dashboard. Roles are scoped to this project, separate from instance administration."
        badge={<StatusBadge tone="emerald" dot pulse>{active} active</StatusBadge>}
        actions={
          <>
            <Button variant="outline">Access policy</Button>
            <Button onClick={() => setInviteOpen(true)}>
              <MailIcon size={14} />
              Invite member
            </Button>
          </>
        }
      />

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Members"
          description="A project must always retain at least one active Owner."
          action={
            <Button variant="link" size="sm" className="h-auto px-0 text-xs">
              Export CSV
            </Button>
          }
        >
          <div className="mb-4 grid grid-cols-2 divide-x divide-border md:grid-cols-4">
            <StatTile
              Icon={UsersRoundIcon}
              iconBg={tint.indigo.bg}
              iconColor={tint.indigo.text}
              label="Members"
              value={members.length}
              hint={`${active} active`}
            />
            <StatTile
              Icon={ShieldCheckIcon}
              iconBg={tint.violet.bg}
              iconColor={tint.violet.text}
              label="Owners"
              value={owners.length}
              hint="Project control"
            />
            <StatTile
              Icon={UserCogIcon}
              iconBg={tint.blue.bg}
              iconColor={tint.blue.text}
              label="Admins"
              value={members.filter((m) => m.role === "ADMIN").length}
              hint="Manage resources"
            />
            <StatTile
              Icon={ClockIcon}
              iconBg={tint.amber.bg}
              iconColor={tint.amber.text}
              label="Pending"
              value={pending}
              hint="Awaiting accept"
            />
          </div>

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
                  <TableHead className="w-8" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {members.map((member) => {
                  const role = roleMeta[member.role];
                  const status = statusMeta[member.status];
                  const isLastOwner =
                    member.role === "OWNER" && owners.length <= 1;
                  return (
                    <TableRow key={member.id}>
                      <TableCell>
                        <div className="flex items-center gap-2.5">
                          <Avatar className="size-7">
                            <AvatarFallback
                              className={`size-7 text-[10px] font-semibold text-white ${colorFor(member.email)}`}
                            >
                              {initials(member.name)}
                            </AvatarFallback>
                          </Avatar>
                          <div className="flex flex-col">
                            <span className="flex items-center gap-1.5 font-medium text-foreground">
                              {member.name}
                              {member.you ? (
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
                            {(Object.keys(roleMeta) as Role[]).map((r) => (
                              <DropdownMenuItem
                                key={r}
                                disabled={r === "OWNER" && member.role === "OWNER"}
                                onClick={() => changeRole(member.id, r)}
                              >
                                {roleMeta[r].label}
                              </DropdownMenuItem>
                            ))}
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                      <TableCell>
                        <StatusBadge tone={status.tone} dot={status.dot}>
                          {status.label}
                        </StatusBadge>
                      </TableCell>
                      <TableCell>
                        {member.mfa ? (
                          <StatusBadge tone="emerald">On</StatusBadge>
                        ) : (
                          <span className="text-xs text-muted-foreground">Off</span>
                        )}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {member.lastActive}
                      </TableCell>
                      <TableCell>
                        <DropdownMenu modal={false}>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              aria-label={`Actions for ${member.name}`}
                            >
                              <EllipsisIcon size={14} />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-44">
                            <DropdownMenuItem>Resend invite</DropdownMenuItem>
                            <DropdownMenuItem>Manage access</DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              variant="destructive"
                              disabled={isLastOwner}
                              onClick={() => setRemoveTarget(member)}
                              className="text-destructive focus:text-destructive"
                            >
                              <LogOut className="size-3.5" />
                              Remove
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

          <div className="mt-4 flex items-center gap-2 border-t pt-3 text-[11px] text-muted-foreground">
            <ShieldCheckIcon size={14} className="shrink-0 text-violet-500" />
            Project administration is a membership concern — the{" "}
            <span className="text-foreground">instanceAdmin</span> flag grants
            global instance access, not project roles.
          </div>
        </Panel>
      </Stagger>

      {/* Invite dialog */}
      <Dialog open={inviteOpen} onOpenChange={setInviteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Invite member</DialogTitle>
            <DialogDescription>
              The invitee must have a Nexus account. They&apos;ll be added to
              this project with the chosen role.
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
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Role</Label>
              <Select value={inviteRole} onValueChange={(v) => setInviteRole(v as Role)}>
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="OWNER">Owner — control project &amp; memberships</SelectItem>
                  <SelectItem value="ADMIN">Admin — manage configuration &amp; resources</SelectItem>
                  <SelectItem value="MEMBER">Member — limited dashboard access</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button onClick={invite}>Send invite</Button>
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
            <DialogTitle>Remove {removeTarget?.name}?</DialogTitle>
            <DialogDescription>
              {removeTarget?.name} ({removeTarget?.email}) will lose access to
              this project immediately. This is recorded in the audit log.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              variant="destructive"
              onClick={() => {
                if (removeTarget) removeMember(removeTarget.id);
                setRemoveTarget(null);
              }}
            >
              Remove member
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}
