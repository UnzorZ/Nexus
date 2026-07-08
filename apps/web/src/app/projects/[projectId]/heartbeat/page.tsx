"use client";

import { useEffect, useState } from "react";
import { Info } from "lucide-react";
import { ActivityIcon } from "@/components/ui/activity";
import { ClockIcon } from "@/components/ui/clock";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
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
import {
  useProjectHeartbeats,
  useProjectRegistrySettings,
  useSaveRegistrySettings,
} from "@/features/heartbeat/queries";
import { toMessage } from "@/lib/api/errors";
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
  const {
    data: instances,
    isLoading: loading,
    error: hbError,
    refetch,
  } = useProjectHeartbeats(project?.id ?? "");
  const error = hbError ? toMessage(hbError) : null;
  const refresh = () => refetch();

  const settingsQ = useProjectRegistrySettings(project?.id ?? "");
  const saveSettingsM = useSaveRegistrySettings(project?.id ?? "");
  const [notifyEnabled, setNotifyEnabled] = useState(false);
  const [notifyEmailsText, setNotifyEmailsText] = useState("");
  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect */
    if (settingsQ.data) {
      setNotifyEnabled(settingsQ.data.offlineNotifyEnabled);
      setNotifyEmailsText((settingsQ.data.offlineNotifyRecipients ?? []).join("\n"));
    }
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [settingsQ.data]);
  const settingsError = saveSettingsM.error ? toMessage(saveSettingsM.error) : null;
  const canManage = project?.canManage ?? false;

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

  async function onSaveOfflineAlert() {
    if (!settingsQ.data) return;
    const recipients = notifyEnabled
      ? notifyEmailsText
          .split("\n")
          .map((s) => s.trim())
          .filter((s) => s.length > 0)
      : [];
    await saveSettingsM.mutateAsync({
      intervalSeconds: settingsQ.data.intervalSeconds,
      timeoutSeconds: settingsQ.data.timeoutSeconds,
      offlineNotifyEnabled: notifyEnabled,
      offlineNotifyRecipients: recipients,
    });
  }

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
                        <MonoChip>{inst.apiKeyPrefix}</MonoChip>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </Panel>

        <Panel
          title="Offline alerts"
          description="Get an email when an instance has been offline for more than 5 minutes (one alert per outage)."
        >
          <div className="flex flex-col gap-4">
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={notifyEnabled}
                onChange={(e) => setNotifyEnabled(e.target.checked)}
                disabled={!canManage}
                className="size-4 accent-[var(--accent)]"
              />
              Enable offline alerts
            </label>
            {canManage ? (
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="hb-notify-recipients">Alert recipients</Label>
                <textarea
                  id="hb-notify-recipients"
                  placeholder={"ops@example.com\noncall@example.com"}
                  value={notifyEmailsText}
                  onChange={(e) => setNotifyEmailsText(e.target.value)}
                  disabled={!notifyEnabled}
                  rows={3}
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm shadow-sm outline-none transition focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                  spellCheck={false}
                />
                <p className="text-[11px] text-muted-foreground">
                  One email per line. Each recipient gets the offline alert.
                </p>
              </div>
            ) : settingsQ.data?.offlineNotifyEnabled ? (
              <p className="text-sm text-muted-foreground">
                Alerts to {(settingsQ.data.offlineNotifyRecipients ?? []).join(", ") || "—"}.
              </p>
            ) : (
              <p className="text-sm text-muted-foreground">Disabled.</p>
            )}
            {settingsError ? (
              <p className="text-xs text-destructive">{settingsError}</p>
            ) : null}
            {canManage ? (
              <div>
                <Button
                  size="sm"
                  onClick={onSaveOfflineAlert}
                  disabled={
                    saveSettingsM.isPending ||
                    (notifyEnabled &&
                      notifyEmailsText
                        .split("\n")
                        .map((s) => s.trim())
                        .filter((s) => s.length > 0).length === 0)
                  }
                >
                  {saveSettingsM.isPending ? "Saving…" : "Save alerts"}
                </Button>
              </div>
            ) : null}
          </div>
        </Panel>
      </Stagger>
    </Stagger>
  );
}
