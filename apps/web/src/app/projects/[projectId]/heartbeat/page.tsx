"use client";

import { Info } from "lucide-react";
import { ActivityIcon } from "@/components/ui/activity";
import { ClockIcon } from "@/components/ui/clock";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { Skeleton } from "@/components/ui/skeleton";
import { Stagger } from "@/components/dashboard/anim";
import {
  EmptyState,
  MonoChip,
  PageHeader,
  Panel,
  StatusBadge,
} from "@/components/dashboard/shared";
import { livenessMeta, formatRelativeTime } from "@/features/heartbeat/display";
import { useProjectHeartbeats } from "@/features/heartbeat/useProjectHeartbeats";
import { useProject } from "../useProject";

/** Offline timeout (server default, spec §13.1). Shown read-only. */
const OFFLINE_TIMEOUT_SECONDS = 90;

function HeartbeatsLoading() {
  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Stagger className="mt-6">
        <Panel title="Instances">
          <div className="flex flex-col gap-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        </Panel>
      </Stagger>
    </Stagger>
  );
}

export default function ProjectHeartbeatPage() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const { instances, loading, error, refresh } = useProjectHeartbeats(
    project?.id ?? "",
  );

  const name = project?.name ?? "...";
  const loadingState = projectLoading || (Boolean(project) && loading);

  if (loadingState) {
    return <HeartbeatsLoading />;
  }

  if (projectError || error) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", name, "Heartbeat"]}
          title="Heartbeat"
          description=""
          projectId={project?.id}
        />
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              title="Could not load heartbeat data"
              description={projectError ?? error ?? "Unknown error"}
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

  if (!project || !instances) {
    return null;
  }

  const online = instances.filter((i) => i.liveness === "ONLINE").length;

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, "Heartbeat"]}
        title="Heartbeat"
        description="App instances reporting liveness to Nexus. Status is derived from last seen and the liveness timeout — not pushed by clients."
        projectId={project.id}
        badge={
          <StatusBadge tone="emerald" dot pulse>
            {online} online
          </StatusBadge>
        }
      />

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Instances"
          description="Each heartbeat is tied to the API key that reported it."
        >
          <div className="mb-4 flex items-center gap-2 text-[11px] text-muted-foreground">
            <ClockIcon size={14} className="shrink-0 text-emerald-500" />
            An instance is{" "}
            <span className="text-foreground">offline</span> after{" "}
            {OFFLINE_TIMEOUT_SECONDS}s with no heartbeat.{" "}
            <span className="text-foreground">Stale</span> sits in the grace
            window before that.
          </div>

          {instances.length === 0 ? (
            <EmptyState
              Icon={ActivityIcon}
              title="No instances reporting"
              description="Create an API key with the registry:heartbeat scope and have your app POST to /api/v1/registry/heartbeat."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Instance</TableHead>
                  <TableHead className="w-32 whitespace-nowrap">
                    Status
                  </TableHead>
                  <TableHead className="w-44 whitespace-nowrap">
                    Last seen
                  </TableHead>
                  <TableHead className="w-48 whitespace-nowrap">
                    <span className="inline-flex items-center gap-1">
                      Reported by
                      <TooltipProvider>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <button
                              type="button"
                              aria-label="Reported by: the API key that reported this heartbeat"
                              className="text-muted-foreground transition-colors hover:text-foreground"
                            >
                              <Info size={13} />
                            </button>
                          </TooltipTrigger>
                          <TooltipContent>
                            The API key that last reported this heartbeat.
                          </TooltipContent>
                        </Tooltip>
                      </TooltipProvider>
                    </span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {instances.map((inst) => {
                  const meta = livenessMeta[inst.liveness];
                  return (
                    <TableRow key={inst.id}>
                      <TableCell>
                        <div className="flex flex-col">
                          <MonoChip>{inst.instanceId}</MonoChip>
                          <span className="mt-0.5 text-[11px] text-muted-foreground">
                            {inst.appName}
                            {inst.appVersion ? ` · v${inst.appVersion}` : ""}
                          </span>
                        </div>
                      </TableCell>
                      <TableCell>
                        <StatusBadge
                          tone={meta.tone}
                          dot={meta.dot}
                          pulse={meta.pulse}
                        >
                          {meta.label}
                        </StatusBadge>
                        {inst.status && inst.status !== "up" ? (
                          <span className="ml-2 text-[11px] text-muted-foreground">
                            reported {inst.status}
                          </span>
                        ) : null}
                      </TableCell>
                      <TableCell>
                        <span className="flex items-center gap-1.5 whitespace-nowrap tabular-nums text-muted-foreground">
                          {inst.liveness === "ONLINE" ? (
                            <span className="nexus-live relative h-1.5 w-1.5 rounded-full bg-emerald-500 text-emerald-500" />
                          ) : null}
                          {formatRelativeTime(inst.lastSeenAt)}
                        </span>
                      </TableCell>
                      <TableCell>
                        <MonoChip>{inst.apiKeyId.slice(0, 8)}</MonoChip>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </Panel>
      </Stagger>
    </Stagger>
  );
}
