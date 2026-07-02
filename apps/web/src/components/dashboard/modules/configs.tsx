"use client";

import type { ComponentType } from "react";
import { NotifyModule } from "./notify";
import { StorageModule } from "./storage";
import { VaultModule } from "./vault";
import { BackupModule } from "./backup";
import { ConfigModule } from "./config";
import { MetricsModule } from "./metrics";
import { IdentityModule } from "./identity";
import { PermissionsModule } from "./permissions";
import { RegistryModule } from "./registry";
import { AuditModule } from "./audit";

/** Module key → its configuration component (rendered inside ModuleShell). */
export const MODULE_CONFIGS: Record<string, ComponentType> = {
  notify: NotifyModule,
  storage: StorageModule,
  vault: VaultModule,
  backup: BackupModule,
  config: ConfigModule,
  metrics: MetricsModule,
  identity: IdentityModule,
  permissions: PermissionsModule,
  registry: RegistryModule,
  audit: AuditModule,
};
