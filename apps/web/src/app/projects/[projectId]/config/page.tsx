"use client";

import { SettingsIcon } from "@/components/ui/settings";
import { ModulePage } from "@/components/dashboard/ModulePage";
import { ConfigModule } from "@/components/dashboard/modules/config";

/**
 * Config — acceso top-level (sidebar) a la configuración dinámica y feature
 * flags del proyecto. Reutiliza el mismo componente CRUD que /modules/config.
 */
export default function ProjectConfigPage() {
  return (
    <ModulePage
      moduleKey="config"
      title="Config"
      description="Dynamic configuration values and feature flags your apps read at runtime."
      Icon={SettingsIcon}
    >
      <ConfigModule />
    </ModulePage>
  );
}
