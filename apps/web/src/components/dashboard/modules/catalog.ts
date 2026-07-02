"use client";

import { useSyncExternalStore } from "react";
import {
  Activity,
  Archive,
  Bell,
  Gauge,
  HardDrive,
  KeyRound,
  ScrollText,
  Settings,
  ShieldCheck,
  Users,
} from "lucide-react";
import { tint } from "@/components/dashboard/anim";
import type { Tone } from "@/components/dashboard/shared";

export type Category = "Access" | "Integration" | "Data" | "Operations";

export type ModuleMeta = {
  key: string;
  name: string;
  description: string;
  category: Category;
  Icon: React.ElementType;
  iconBg: string;
  iconColor: string;
};

export const categoryTone: Record<Category, Tone> = {
  Access: "violet",
  Integration: "cyan",
  Data: "blue",
  Operations: "amber",
};

export const MODULE_CATALOG: ModuleMeta[] = [
  { key: "identity", name: "Identity", description: "Project-isolated users, OAuth/OIDC realms, JWT/JWKS issuance.", category: "Access", Icon: Users, iconBg: tint.indigo.bg, iconColor: tint.indigo.text },
  { key: "permissions", name: "Permissions", description: "Permission catalog, roles, assignments and snapshot API.", category: "Access", Icon: ShieldCheck, iconBg: tint.violet.bg, iconColor: tint.violet.text },
  { key: "audit", name: "Audit", description: "Browseable audit trail for sensitive project actions.", category: "Access", Icon: ScrollText, iconBg: tint.amber.bg, iconColor: tint.amber.text },
  { key: "registry", name: "Registry", description: "App registration and heartbeat-based liveness tracking.", category: "Integration", Icon: Activity, iconBg: tint.cyan.bg, iconColor: tint.cyan.text },
  { key: "notify", name: "Notify", description: "Notification requests, templates and email delivery.", category: "Integration", Icon: Bell, iconBg: tint.emerald.bg, iconColor: tint.emerald.text },
  { key: "storage", name: "Storage", description: "Per-project blob storage and object lifecycle.", category: "Data", Icon: HardDrive, iconBg: tint.blue.bg, iconColor: tint.blue.text },
  { key: "vault", name: "Vault", description: "Encrypted secrets and project-scoped key values.", category: "Data", Icon: KeyRound, iconBg: tint.red.bg, iconColor: tint.red.text },
  { key: "backup", name: "Backup", description: "Scheduled project data snapshots and restore points.", category: "Data", Icon: Archive, iconBg: tint.indigo.bg, iconColor: tint.indigo.text },
  { key: "config", name: "Config", description: "Dynamic project configuration and feature flags.", category: "Operations", Icon: Settings, iconBg: tint.amber.bg, iconColor: tint.amber.text },
  { key: "metrics", name: "Metrics", description: "Project-scoped usage metrics and instrumentation.", category: "Operations", Icon: Gauge, iconBg: tint.cyan.bg, iconColor: tint.cyan.text },
];

export const CORE_CAPABILITIES = [
  "Project management",
  "API key validation",
  "Audit write path",
  "Admin dashboard access",
];

// MVP default per spec §16.2 + the audit module for browseable history.
const DEFAULT_ENABLED: Record<string, boolean> = {
  identity: true,
  permissions: true,
  registry: true,
  audit: true,
};

/* -------------------------------------------------------------------------- */
/*  Shared enable/disable store — persists across navigation (index ↔ detail)  */
/* -------------------------------------------------------------------------- */

let enabledState: Record<string, boolean> = Object.fromEntries(
  MODULE_CATALOG.map((m) => [m.key, DEFAULT_ENABLED[m.key] ?? false]),
);
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((l) => l());
}

export function setModuleEnabled(key: string, value: boolean) {
  if (enabledState[key] === value) return;
  enabledState = { ...enabledState, [key]: value };
  emit();
}

export function toggleModuleEnabled(key: string) {
  setModuleEnabled(key, !(enabledState[key] ?? false));
}

function subscribe(callback: () => void) {
  listeners.add(callback);
  return () => {
    listeners.delete(callback);
  };
}

function getSnapshot() {
  return enabledState;
}

/** Live map of module key → enabled. Re-renders subscribers on change. */
export function useModuleEnabled(): Record<string, boolean> {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
}

export function getModule(key: string): ModuleMeta | undefined {
  return MODULE_CATALOG.find((m) => m.key === key);
}
