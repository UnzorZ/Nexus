"use client";

import { GaugeIcon } from "@/components/ui/gauge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Area,
  AreaChart,
  CartesianGrid,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { ChartContainer, ChartTooltipContent } from "@/components/ui/chart";
import { Stagger } from "@/components/dashboard/anim";
import { EmptyState, Panel, StatusBadge } from "@/components/dashboard/shared";
import { useProjectMetrics } from "@/features/metrics/queries";
import type { MetricSeries } from "@/features/metrics/api";
import { toMessage } from "@/lib/api/errors";
import { useProject } from "@/app/projects/[projectId]/useProject";

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
  return Number.isInteger(value) ? value.toString() : value.toFixed(2);
}

function chartData(series: MetricSeries) {
  // points arrive newest-first from the API; reverse for chronological left→right.
  return [...series.points]
    .map((p) => ({ t: formatRelative(p.recordedAt), value: p.value }))
    .reverse();
}

/** Clave estable (nombre + tags ordenados): distingue series del mismo nombre con
 *  tagsets distintos (M7c2) — usada como React key y para el id del gradient SVG. */
function seriesKey(series: MetricSeries): string {
  const tags = series.tags ?? {};
  const tagPart = Object.keys(tags)
    .sort()
    .map((k) => `${k}=${tags[k]}`)
    .join(",");
  return tagPart ? `${series.name}|${tagPart}` : series.name;
}

function MetricSeriesCard({ series }: { series: MetricSeries }) {
  const data = chartData(series);
  const gradientId = `metric-grad-${seriesKey(series).replace(/[^a-z0-9]/gi, "")}`;
  const tags = series.tags ?? {};
  const tagEntries = Object.keys(tags).sort().map((k) => [k, tags[k]] as const);
  return (
    <Panel>
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <code className="truncate font-mono text-sm font-semibold text-foreground">
            {series.name}
          </code>
          {tagEntries.length > 0 && (
            <div className="flex flex-wrap gap-1">
              {tagEntries.map(([k, v]) => (
                <span
                  key={k}
                  className="rounded bg-muted px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground"
                >
                  {k}={v}
                </span>
              ))}
            </div>
          )}
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
        {data.length > 1 ? (
          <ChartContainer className="h-[140px] w-full">
            <AreaChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: 4 }}>
              <defs>
                <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--chart-2)" stopOpacity={0.35} />
                  <stop offset="100%" stopColor="var(--chart-2)" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid
                vertical={false}
                strokeDasharray="3 3"
                className="stroke-muted/60"
              />
              <XAxis dataKey="t" hide />
              <YAxis
                hide
                domain={["auto", "auto"]}
                allowDecimals
              />
              <Tooltip
                cursor={{ stroke: "var(--chart-2)", strokeWidth: 1 }}
                content={
                  <ChartTooltipContent
                    valueFormatter={(v) => formatValue(Number(v))}
                  />
                }
              />
              <Area
                type="monotone"
                dataKey="value"
                stroke="var(--chart-2)"
                strokeWidth={2}
                fill={`url(#${gradientId})`}
                dot={false}
                isAnimationActive={false}
              />
            </AreaChart>
          </ChartContainer>
        ) : (
          <div className="flex h-[140px] items-center justify-center text-xs text-muted-foreground">
            Needs at least 2 points to chart
          </div>
        )}
      </div>
    </Panel>
  );
}

export function MetricsModule() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const projectId = project?.id ?? "";

  const metricsQ = useProjectMetrics(projectId);
  const series = metricsQ.data ?? null;
  const loading = metricsQ.isLoading;
  const error = metricsQ.error ? toMessage(metricsQ.error) : null;
  const refresh = () => metricsQ.refetch();

  const loadingState = projectLoading || (Boolean(project) && loading);

  if (loadingState) {
    return (
      <Panel title="Metrics">
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-40 w-full rounded-lg" />
          ))}
        </div>
      </Panel>
    );
  }

  if (projectError || error) {
    return (
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
    );
  }

  if (!project || !series) {
    return null;
  }

  return (
    <Stagger className="grid flex-1 grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
      {series.length === 0 ? (
        <Panel className="col-span-full">
          <EmptyState
            Icon={GaugeIcon}
            title="No metrics reported yet"
            description="Apps report points with POST /api/v1/metrics/record (scope metrics:write). Series appear here once data arrives."
          />
        </Panel>
      ) : (
        series.map((s) => <MetricSeriesCard key={seriesKey(s)} series={s} />)
      )}
    </Stagger>
  );
}
