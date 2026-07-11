"use client";

import { BellIcon } from "@/components/ui/bell";
import { ModulePage } from "@/components/dashboard/ModulePage";
import { NotifyModule } from "@/components/dashboard/modules/notify";

/**
 * Notify — acceso top-level (sidebar) a notificaciones: plantillas, relay SMTP
 * por proyecto, variables globales, email de prueba e historial de entrega.
 * Reutiliza el mismo componente que /modules/notify.
 */
export default function ProjectNotifyPage() {
  return (
    <ModulePage
      moduleKey="notify"
      title="Notify"
      description="Email templates, per-project SMTP relay, global variables, test sends and delivery history."
      Icon={BellIcon}
    >
      <NotifyModule />
    </ModulePage>
  );
}
