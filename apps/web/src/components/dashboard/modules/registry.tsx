"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ActivityIcon } from "@/components/ui/activity";
import { ArrowBigRightIcon } from "@/components/ui/arrow-big-right";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Stagger } from "@/components/dashboard/anim";
import { EmptyState, Panel, StatusBadge } from "@/components/dashboard/shared";
import {
  useProjectHeartbeats,
  useProjectRegistrySettings,
  useSaveRegistrySettings,
} from "@/features/heartbeat/queries";
import {
  formatRelativeTime,
  livenessMeta,
} from "@/features/heartbeat/display";
import type { HeartbeatLiveness } from "@/features/heartbeat/api";
import { toMessage } from "@/lib/api/errors";
import { useProject } from "@/app/projects/[projectId]/useProject";

function countBy(
  instances: { liveness: HeartbeatLiveness }[],
): Record<HeartbeatLiveness, number> {
  const base: Record<HeartbeatLiveness, number> = {
    ONLINE: 0,
    STALE: 0,
    OFFLINE: 0,
  };
  for (const instance of instances) {
    base[instance.liveness] += 1;
  }
  return base;
}

export function RegistryModule() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const projectId = project?.id ?? "";

  const heartbeatsQ = useProjectHeartbeats(projectId);
  const settingsQ = useProjectRegistrySettings(projectId);
  const saveSettingsM = useSaveRegistrySettings(projectId);
  const instances = heartbeatsQ.data ?? null;
  const settings = settingsQ.data ?? null;
  const loading = heartbeatsQ.isLoading;
  const error = heartbeatsQ.error ? toMessage(heartbeatsQ.error) : null;
  const refresh = () => heartbeatsQ.refetch();

  const canManage = project?.canManage ?? false;
  const [thresholds, setThresholds] = useState({
    interval: "30",
    timeout: "90",
  });

  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect */
    if (settings) {
      setThresholds({
        interval: String(settings.intervalSeconds),
        timeout: String(settings.timeoutSeconds),
      });
    }
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [settings]);

  function saveThresholds() {
    saveSettingsM.mutateAsync({
      intervalSeconds: Number(thresholds.interval) || 0,
      timeoutSeconds: Number(thresholds.timeout) || 0,
    });
  }

  const settingsError = saveSettingsM.error
    ? toMessage(saveSettingsM.error)
    : null;

  const loadingState = projectLoading || (Boolean(project) && loading);
  const counts = instances ? countBy(instances) : null;

  if (loadingState) {
    return (
      <Panel title="Registered instances">
        <div className="flex flex-col gap-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      </Panel>
    );
  }

  if (projectError || error) {
    return (
      <Panel>
        <EmptyState
          title="Could not load registry"
          description={projectError ?? error ?? "Unknown error"}
          action={
            <Button variant="outline" onClick={() => refresh()}>
              Retry
            </Button>
          }
        />
      </Panel>
    );
  }

  if (!project || !instances) {
    return null;
  }

  return (
    <Stagger className="grid flex-1 grid-cols-1 gap-6">
      <Panel
        title="Registered instances"
        description="Apps reporting heartbeats via the project API (scope registry:heartbeat). Liveness is derived from the last seen timestamp."
        action={
          <div className="flex items-center gap-2">
            <StatusBadge tone="emerald" dot pulse={counts ? counts.ONLINE > 0 : false}>
              {counts?.ONLINE ?? 0} online
            </StatusBadge>
            {counts && counts.STALE > 0 ? (
              <StatusBadge tone="amber" dot>
                {counts.STALE} stale
              </StatusBadge>
            ) : null}
            {counts && counts.OFFLINE > 0 ? (
              <StatusBadge tone="red" dot>
                {counts.OFFLINE} offline
              </StatusBadge>
            ) : null}
          </div>
        }
      >
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
                <TableHead>App</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Liveness</TableHead>
                <TableHead>Last seen</TableHead>
                <TableHead>Reported by</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {instances.map((instance) => {
                const meta = livenessMeta[instance.liveness];
                return (
                  <TableRow key={instance.id}>
                    <TableCell>
                      <code className="font-mono text-xs text-foreground">
                        {instance.instanceId}
                      </code>
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-col">
                        <span className="font-medium">{instance.appName}</span>
                        {instance.appVersion ? (
                          <span className="font-mono text-[11px] text-muted-foreground">
                            {instance.appVersion}
                          </span>
                        ) : null}
                      </div>
                    </TableCell>
                    <TableCell>
                      <code className="font-mono text-xs text-muted-foreground">
                        {instance.status}
                      </code>
                    </TableCell>
                    <TableCell>
                      <StatusBadge tone={meta.tone} dot={meta.dot} pulse={meta.pulse}>
                        {meta.label}
                      </StatusBadge>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {formatRelativeTime(instance.lastSeenAt)}
                    </TableCell>
                    <TableCell>
                      <code className="font-mono text-xs text-muted-foreground">
                        {instance.apiKeyPrefix}
                      </code>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </Panel>

      <Panel
        title="Liveness thresholds"
        description="How Nexus decides what's alive, per project. Standard = ONLINE window; Timeout = OFFLINE cap (STALE in between). Must satisfy standard ≤ timeout."
        action={
          settings?.overridden ? (
            <StatusBadge tone="amber">Project override</StatusBadge>
          ) : (
            <StatusBadge tone="slate">Instance defaults</StatusBadge>
          )
        }
      >
        <div className="flex flex-col gap-4">
          {canManage ? (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="reg-standard">Standard (online, s)</Label>
                <Input
                  id="reg-standard"
                  type="number"
                  min={1}
                  value={thresholds.interval}
                  onChange={(e) =>
                    setThresholds((t) => ({ ...t, interval: e.target.value }))
                  }
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="reg-timeout">Timeout (s)</Label>
                <Input
                  id="reg-timeout"
                  type="number"
                  min={1}
                  value={thresholds.timeout}
                  onChange={(e) =>
                    setThresholds((t) => ({ ...t, timeout: e.target.value }))
                  }
                />
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
              <div>Standard: {settings?.intervalSeconds ?? "—"}s</div>
              <div>Timeout: {settings?.timeoutSeconds ?? "—"}s</div>
            </div>
          )}
          {settingsError ? (
            <p className="text-xs text-destructive">{settingsError}</p>
          ) : null}
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-xs text-muted-foreground">
              ONLINE within standard · STALE until timeout · OFFLINE after.
            </p>
            <div className="flex items-center gap-2">
              <Button asChild variant="outline" size="sm">
                <Link href={`/projects/${projectId}/heartbeat`}>
                  Full Heartbeat page
                  <ArrowBigRightIcon size={14} />
                </Link>
              </Button>
              {canManage ? (
                <Button
                  size="sm"
                  onClick={saveThresholds}
                  disabled={saveSettingsM.isPending}
                >
                  {saveSettingsM.isPending ? "Saving…" : "Save thresholds"}
                </Button>
              ) : null}
            </div>
          </div>
        </div>
      </Panel>
    </Stagger>
  );
}
