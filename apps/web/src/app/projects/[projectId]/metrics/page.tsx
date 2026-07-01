"use client";

import { GaugeIcon } from "@/components/ui/gauge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Stagger } from "@/components/dashboard/anim";
import {
  EmptyState,
  PageHeader,
  Panel,
  StatusBadge,
} from "@/components/dashboard/shared";
import { useProjectMetrics } from "@/features/metrics/queries";
import type { MetricSeries } from "@/features/metrics/api";
import { toMessage } from "@/lib/api/errors";
import { useProject } from "../useProject";

function MetricsLoading() {
  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Stagger className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-2">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-32 w-full rounded-lg" />
        ))}
      </Stagger>
    </Stagger>
  );
}

function formatRelative(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const sec = Math.round(diffMs / 1000);
  if (sec < 60) return `${sec}s ago`;
  const min = Math.round(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.round(min / 60);
  if (hr < 24) return `${hr}h ago`;
  return `${Math.round(hr / 24)}d ago`;
}

function formatValue(value: number): string {
  return Number.isInteger(value)
    ? value.toString()
    : value.toFixed(2);
}

function Sparkline({ points }: { points: { value: number }[] }) {
  if (points.length === 0) return null;
  const max = Math.max(...points.map((p) => p.value), 1);
  return (
    <div className="flex h-12 items-end gap-0.5">
      {points.map((p, i) => (
        <div
          key={i}
          className="flex-1 rounded-sm bg-primary/70"
          style={{ height: `${Math.max((p.value / max) * 100, 4)}%` }}
          title={formatValue(p.value)}
        />
      ))}
    </div>
  );
}

function SeriesCard({ series }: { series: MetricSeries }) {
  return (
    <Panel>
      <div className="flex items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <code className="font-mono text-sm font-semibold text-foreground">
            {series.name}
          </code>
          <div className="flex items-center gap-2">
            <StatusBadge tone="emerald" dot>
              {formatValue(series.lastValue)}
            </StatusBadge>
            <span className="text-xs text-muted-foreground">
              last {formatRelative(series.lastRecordedAt)} · {series.pointCount}{" "}
              pts
            </span>
          </div>
        </div>
      </div>
      <div className="mt-4">
        <Sparkline points={series.points} />
      </div>
    </Panel>
  );
}

export default function ProjectMetricsPage() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const projectId = project?.id ?? "";

  const metricsQ = useProjectMetrics(projectId);
  const series = metricsQ.data ?? null;
  const loading = metricsQ.isLoading;
  const error = metricsQ.error ? toMessage(metricsQ.error) : null;
  const refresh = () => metricsQ.refetch();

  const name = project?.name ?? "...";
  const loadingState = projectLoading || (Boolean(project) && loading);

  if (loadingState) {
    return <MetricsLoading />;
  }

  if (projectError || error) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", name, "Metrics"]}
          title="Metrics"
          description=""
          projectId={project?.id}
        />
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              title="Could not load metrics"
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

  if (!project || !series) {
    return null;
  }

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, "Metrics"]}
        title="Metrics"
        description={`Usage metrics and instrumentation reported by ${name} via the project API.`}
        projectId={project.id}
        badge={
          series.length > 0 ? (
            <StatusBadge tone="emerald" dot pulse>
              {series.length} series
            </StatusBadge>
          ) : undefined
        }
      />

      <Stagger className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {series.length === 0 ? (
          <Panel className="col-span-full">
            <EmptyState
              Icon={GaugeIcon}
              title="No metrics reported yet"
              description="Apps report points with POST /api/v1/metrics/record (scope metrics:write). Series appear here once data arrives."
            />
          </Panel>
        ) : (
          series.map((s) => <SeriesCard key={s.name} series={s} />)
        )}
      </Stagger>
    </Stagger>
  );
}
