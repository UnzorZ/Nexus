"use client";

import { use } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, PageHeader, Panel } from "@/components/dashboard/shared";
import { Stagger } from "@/components/dashboard/anim";
import { ModuleShell } from "@/components/dashboard/modules/ModuleShell";
import { MODULE_CONFIGS } from "@/components/dashboard/modules/configs";
import { getModule } from "@/components/dashboard/modules/catalog";
import { useProjectModules } from "@/features/modules/useProjectModules";
import { useProject } from "../../useProject";

export default function ProjectModuleDetailPage({
  params,
}: {
  params: Promise<{ projectId: string; moduleKey: string }>;
}) {
  const { projectId, moduleKey } = use(params);
  const { project } = useProject();
  const {
    modules,
    loading,
    error,
    setEnabled,
    toggleError,
    isToggling,
    refresh,
  } = useProjectModules(projectId);

  const mod = getModule(moduleKey);
  const name = project?.name ?? "...";
  const canManage = project?.canManage ?? false;

  if (!mod) {
    return (
      <div className="mx-auto flex w-full max-w-7xl flex-1 items-center">
        <EmptyState
          title="Module not found"
          description={
            <>
              <code className="font-mono">{moduleKey}</code> isn&apos;t a Nexus
              module.
            </>
          }
          action={
            <Button asChild>
              <Link href={`/projects/${projectId}/modules`}>
                Back to modules
              </Link>
            </Button>
          }
        />
      </div>
    );
  }

  const status = modules?.find((m) => m.key === mod.key);
  const Config = MODULE_CONFIGS[mod.key];

  if (loading && status === undefined) {
    return (
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-4 p-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  if (error && !modules) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", name, "Modules", mod.name]}
          projectId={projectId}
          title={mod.name}
          description=""
        />
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              title="Could not load module"
              description={error}
              action={
                <Button variant="outline" onClick={() => refresh()}>
                  Retry
                </Button>
              }
            />
          </Panel>
        </Stagger>
      </Stagger>
    );
  }

  return (
    <>
      {toggleError ? (
        <p className="mx-auto mb-2 w-full max-w-7xl text-sm text-destructive">
          {toggleError}
        </p>
      ) : null}
      <ModuleShell
        module={mod}
        projectId={projectId}
        projectName={name}
        enabled={status?.enabled}
        canToggle={canManage && !isToggling(mod.key)}
        onToggle={
          status
            ? () => setEnabled(mod.key, !status.enabled)
            : undefined
        }
      >
        {Config ? (
          <Config />
        ) : (
          <EmptyState
            title="Nothing to configure"
            description="This module has no project-level settings yet."
          />
        )}
      </ModuleShell>
    </>
  );
}
