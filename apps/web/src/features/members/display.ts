import type { Tone } from "@/components/dashboard/shared";
import type { MemberRole, MemberStatus } from "./api";

export const roleMeta: Record<MemberRole, { label: string; tone: Tone }> = {
  OWNER: { label: "Owner", tone: "violet" },
  ADMIN: { label: "Admin", tone: "blue" },
  MEMBER: { label: "Member", tone: "slate" },
};

export const statusMeta: Record<
  MemberStatus,
  { label: string; tone: Tone; dot?: boolean }
> = {
  ACTIVE: { label: "Active", tone: "emerald", dot: true },
  INVITED: { label: "Invited", tone: "amber" },
  SUSPENDED: { label: "Suspended", tone: "red" },
  REVOKED: { label: "Removed", tone: "slate" },
};

const AVATAR_COLORS = [
  "bg-indigo-600",
  "bg-emerald-600",
  "bg-amber-600",
  "bg-rose-600",
  "bg-cyan-600",
  "bg-violet-600",
];

export function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase())
    .join("");
}

export function colorFor(seed: string) {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) {
    hash = seed.charCodeAt(i) + ((hash << 5) - hash);
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

export function formatLastActive(value: string | null): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(date);
}
