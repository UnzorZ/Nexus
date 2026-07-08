import { tint } from "@/components/dashboard/anim";
import type { Tone } from "@/components/dashboard/shared";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { UserIcon } from "@/components/ui/user";
import { UsersRoundIcon } from "@/components/ui/users-round";
import type { ElementType } from "react";
import type { Severity } from "./api";

export { formatRelativeTime } from "@/lib/format";

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
