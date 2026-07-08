import type { Tone } from "@/components/dashboard/shared";
import type { HeartbeatLiveness } from "./api";

/** Color + etiqueta por liveness derivada (espeja el mock del dashboard). */
export const livenessMeta: Record<
  HeartbeatLiveness,
  { label: string; tone: Tone; dot?: boolean; pulse?: boolean }
> = {
  ONLINE: { label: "Online", tone: "emerald", dot: true, pulse: true },
  STALE: { label: "Stale", tone: "amber", dot: true },
  OFFLINE: { label: "Offline", tone: "red", dot: true },
};

export { formatRelativeTime } from "@/lib/format";
