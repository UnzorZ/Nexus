"use client";

import type { ElementType } from "react";
import Link from "next/link";
import { TriangleAlertIcon } from "@/components/ui/triangle-alert-icon";
import { Button } from "@/components/ui/button";
import { Stagger } from "@/components/dashboard/anim";
import {
  EmptyState,
  PageHeader,
  Panel,
  StatusBadge,
} from "@/components/dashboard/shared";
import { useProjectModules } from "@/features/modules/queries";
import { useProject } from "@/app/projects/[projectId]/useProject";

/**
 * Shell compartido para las páginas top-level respaldadas por un módulo
 * (vault, config, metrics, notify). Muestra la cabecera estándar y, si el módulo
 * está desactivado para el proyecto, un aviso con enlace a Modules en lugar del
 * cuerpo — cuyos endpoints devolverían {@code 403 module_disabled}.
 *
 * El contenido real lo aporta cada módulo como <em>children</em> (ConfigModule,
 * VaultModule, …), que ya gestionan sus propios estados de carga/error/vacío.
 * El estado enabled/disabled viene de {@link useProjectModules} (misma caché que
 * la página de Modules, así un toggle se refleja aquí al instante).
 */
export function ModulePage({
  moduleKey,
  title,
  description,
  Icon,
  children,
}: {
  moduleKey: string;
  title: string;
  description: React.ReactNode;
  Icon: ElementType;
  children: React.ReactNode;
}) {
  const { project } = useProject();
  const projectId = project?.id ?? "";
  const name = project?.name ?? "...";
  const modulesQ = useProjectModules(projectId);

  // undefined mientras carga la lista de módulos → asumimos habilitado para no
  // fl: un 403 pasajero del componente hijo es preferible a mostrar "disabled"
  // por error. En cuanto llega el estado real, si está apagado mostramos el aviso.
  const status = modulesQ.data?.find((m) => m.key === moduleKey);
  const disabled = status ? !status.enabled : false;

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, title]}
        title={
          <span className="inline-flex items-center gap-3">
            <span className="flex h-9 w-9 items-center justify-center rounded-md bg-muted">
              <Icon size={20} className="text-foreground" />
            </span>
            {title}
          </span>
        }
        description={description}
        badge={
          status ? (
            status.enabled ? (
              <StatusBadge tone="emerald" dot pulse>
                Enabled
              </StatusBadge>
            ) : (
              <StatusBadge tone="slate">Disabled</StatusBadge>
            )
          ) : null
        }
        projectId={projectId}
      />

      {disabled ? (
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              Icon={TriangleAlertIcon}
              title={`${title} is disabled`}
              description={
                <>
                  Its API endpoints return{" "}
                  <code className="font-mono">403 module_disabled</code>. Turn it
                  on in Modules to configure and use it.
                </>
              }
              action={
                <Button asChild>
                  <Link href={`/projects/${projectId}/modules/${moduleKey}`}>
                    Enable {title}
                  </Link>
                </Button>
              }
            />
          </Panel>
        </Stagger>
      ) : (
        children
      )}
    </Stagger>
  );
}
