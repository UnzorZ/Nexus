import { tint } from "@/components/dashboard/anim";
import type { Tone } from "@/components/dashboard/shared";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { UserIcon } from "@/components/ui/user";
import { UsersRoundIcon } from "@/components/ui/users-round";
import type { ElementType } from "react";
import type { Severity } from "./api";

const rtf = new Intl.RelativeTimeFormat("en", { numeric: "auto" });

/** "42 seconds ago" / "4 minutes ago" / "3 hours ago" / "2 days ago". */
export function formatRelativeTime(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return iso;
  const secondsAgo = Math.round((then - Date.now()) / 1000);
  const abs = Math.abs(secondsAgo);
  if (abs < 60) return rtf.format(secondsAgo, "second");
  if (abs < 3600) return rtf.format(Math.round(secondsAgo / 60), "minute");
  if (abs < 86400) return rtf.format(Math.round(secondsAgo / 3600), "hour");
  return rtf.format(Math.round(secondsAgo / 86400), "day");
}

export type ActorMeta = {
  label: string;
  tone: Tone;
  Icon: ElementType;
  chipBg: string;
  chipColor: string;
};

const actorMeta: Record<string, ActorMeta> = {
  NEXUS_ACCOUNT: {
    label: "Nexus account",
    tone: "indigo",
    Icon: UsersRoundIcon,
    chipBg: tint.indigo.bg,
    chipColor: tint.indigo.text,
  },
  ANONYMOUS: {
    label: "Anonymous",
    tone: "slate",
    Icon: ShieldCheckIcon,
    chipBg: "bg-muted",
    chipColor: "text-muted-foreground",
  },
};

const defaultActorMeta: ActorMeta = {
  label: "Actor",
  tone: "slate",
  Icon: UserIcon,
  chipBg: "bg-muted",
  chipColor: "text-muted-foreground",
};

export function actorMetaFor(actorType: string): ActorMeta {
  return actorMeta[actorType] ?? defaultActorMeta;
}

export const severityMeta: Record<Severity, { label: string; tone: Tone }> = {
  INFO: { label: "Info", tone: "blue" },
  WARNING: { label: "Warning", tone: "amber" },
  MODERATE: { label: "Moderate", tone: "violet" },
  CRITICAL: { label: "Critical", tone: "red" },
};
