"use client";

import { LockIcon } from "@/components/ui/lock";
import { ModulePage } from "@/components/dashboard/ModulePage";
import { VaultModule } from "@/components/dashboard/modules/vault";

/**
 * Vault — acceso top-level (sidebar) al módulo de secretos. Reutiliza el mismo
 * componente CRUD que el detalle bajo /modules/vault; el shell (cabecera +
 * disabled-guard) lo aporta ModulePage. El toggle de módulo vive en Modules.
 */
export default function ProjectVaultPage() {
  return (
    <ModulePage
      moduleKey="vault"
      title="Vault"
      description="Encrypted, project-scoped secrets. Values are masked unless revealed — revealing and rotating are audited."
      Icon={LockIcon}
    >
      <VaultModule />
    </ModulePage>
  );
}
