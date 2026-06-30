"use client";

import { useRouter } from "next/navigation";
import { ChevronRightIcon } from "@/components/ui/chevron-right";
import { TriangleAlertIcon } from "@/components/ui/triangle-alert-icon";
import { Switch } from "@/components/ui/switch";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Stagger } from "@/components/dashboard/anim";
import {
  CORE_CAPABILITIES,
  MODULE_CATALOG,
  categoryTone,
} from "@/components/dashboard/modules/catalog";
import {
  EmptyState,
  PageHeader,
  Panel,
  StatusBadge,
} from "@/components/dashboard/shared";
import {
  MODULE_MESSAGES,
  useProjectModules,
  useSetModuleEnabled,
} from "@/features/modules/queries";
import { toMessage } from "@/lib/api/errors";
import { useProject } from "../useProject";

function ModulesLoading() {
  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Stagger className="mt-6">
        <Panel title="Project modules">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-40 w-full rounded-lg" />
            ))}
          </div>
        </Panel>
      </Stagger>
    </Stagger>
  );
}

export default function ProjectModulesPage() {
  const router = useRouter();
  const { project, loading: projectLoading, error: projectError } = useProject();
  const projectId = project?.id ?? "";
  const modulesQ = useProjectModules(projectId);
  const { setEnabled, isToggling, error: toggleErr } =
    useSetModuleEnabled(projectId);
  const modules = modulesQ.data ?? null;
  const modulesLoading = modulesQ.isLoading;
  const modulesError = modulesQ.error ? toMessage(modulesQ.error) : null;
  const toggleError = toggleErr ? toMessage(toggleErr, MODULE_MESSAGES) : null;
  const refresh = () => modulesQ.refetch();

  const loading = projectLoading || (Boolean(project) && modulesLoading);
  const name = project?.name ?? "...";
  const canManage = project?.canManage ?? false;

  const enabledMap = Object.fromEntries(
    (modules ?? []).map((m) => [m.key, m.enabled]),
  );
  const enabledCount = MODULE_CATALOG.filter((m) => enabledMap[m.key]).length;

  function open(key: string) {
    if (!project) return;
    router.push(`/projects/${project.id}/modules/${key}`);
  }

  if (loading) {
    return <ModulesLoading />;
  }

  if (projectError || modulesError) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", name, "Modules"]}
          title="Modules"
          description=""
          projectId={project?.id}
        />
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              title="Could not load modules"
              description={projectError ?? modulesError ?? "Unknown error"}
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

  if (!project || !modules) {
    return null;
  }

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, "Modules"]}
        title="Modules"
        description={`Enable or disable Nexus capabilities for ${name}. Disabled modules are saved, but request-level enforcement isn't active yet.`}
        projectId={project.id}
        badge={
          <StatusBadge tone="emerald" dot pulse>
            {enabledCount} enabled
          </StatusBadge>
        }
        actions={
          <>
            <Button variant="outline">Documentation</Button>
            <Button>Sync declarations</Button>
          </>
        }
      />

      {toggleError ? (
        <p className="mt-4 text-sm text-destructive">{toggleError}</p>
      ) : null}

      {project.status !== "ACTIVE" ? (
        <div className="mt-4 flex items-start gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-700 dark:text-amber-300">
          <TriangleAlertIcon size={16} className="mt-0.5 shrink-0" />
          <span>
            This project is{" "}
            <strong>
              {project.status.charAt(0) + project.status.slice(1).toLowerCase()}
            </strong>
            . You can still change modules and your changes are saved, but they
            aren&apos;t enforced yet — disabling a module won&apos;t reject
            requests until module gating is enabled.
          </span>
        </div>
      ) : null}

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Project modules"
          description="Open a module to configure it, or toggle it to enable or disable its APIs."
        >
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
            {MODULE_CATALOG.map((mod) => {
              const isOn = !!enabledMap[mod.key];
              return (
                <div
                  key={mod.key}
                  role="button"
                  tabIndex={0}
                  onClick={() => open(mod.key)}
                  onKeyDown={(e) => {
                    if (e.currentTarget !== e.target) return;
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      open(mod.key);
                    }
                  }}
                  className={`group flex min-h-10 cursor-pointer flex-col gap-3 rounded-lg p-4 transition-[border-color,box-shadow] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/40 ${
                    isOn
                      ? "bg-card ring-1 ring-border hover:ring-primary/30"
                      : "border border-dashed border-border bg-muted/40 hover:border-primary/30"
                  }`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-muted transition-transform duration-200 group-hover:scale-110">
                      <mod.Icon size={18} className="text-foreground" />
                    </div>
                    <Switch
                      checked={isOn}
                      disabled={!canManage || isToggling(mod.key)}
                      className="enabled:hover:border-primary"
                      onClick={(e) => e.stopPropagation()}
                      onCheckedChange={() => setEnabled(mod.key, !isOn)}
                      aria-label={`Toggle ${mod.name}`}
                    />
                  </div>

                  <div className="flex flex-col gap-1">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold">{mod.name}</span>
                      <StatusBadge tone={categoryTone[mod.category]}>
                        {mod.category}
                      </StatusBadge>
                    </div>
                    <p className="text-xs leading-relaxed text-muted-foreground">
                      {mod.description}
                    </p>
                  </div>

                  <div className="mt-auto flex items-center justify-between border-t pt-3">
                    {isOn ? (
                      <StatusBadge tone="emerald" dot>
                        Enabled
                      </StatusBadge>
                    ) : (
                      <StatusBadge tone="slate">Disabled</StatusBadge>
                    )}
                    <span className="inline-flex items-center gap-1 text-xs font-medium text-primary transition-transform group-hover:translate-x-0.5">
                      {isOn ? "Configure" : "Open"}
                      <ChevronRightIcon size={14} />
                    </span>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="mt-5 flex flex-col gap-2 border-t pt-4 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
            <p>
              Core capabilities are always enabled and cannot be disabled:
              <span className="ml-1 text-foreground">
                {CORE_CAPABILITIES.join(" · ")}
              </span>
              .
            </p>
            <Button variant="link" size="sm" className="h-auto px-0 text-xs">
              View module reference
            </Button>
          </div>
        </Panel>
      </Stagger>
    </Stagger>
  );
}
