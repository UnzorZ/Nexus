import type { Tone } from "@/components/dashboard/shared";
import type { MemberRole, MemberStatus } from "./api";

// Utilidades de avatar compartidas (re-exportadas para no romper imports).
export { colorFor, initials } from "@/lib/account";

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

export function formatLastActive(value: string | null): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(date);
}
