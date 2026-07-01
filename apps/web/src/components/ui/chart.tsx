"use client";

import * as React from "react";
import { ResponsiveContainer } from "recharts";
import { cn } from "@/lib/utils";

/**
 * Minimal shadcn-style chart wrapper around recharts. The container gives the
 * chart a sized box (set height via `className`, e.g. `h-[160px]`) and a
 * ResponsiveContainer so the chart fills it. Series colors use the project's
 * `--chart-1..5` / `--primary` CSS vars so they adapt to light/dark.
 */
export function ChartContainer({
  className,
  children,
  ...props
}: React.ComponentProps<"div"> & { children: React.ReactElement }) {
  return (
    <div
      className={cn("flex w-full items-center justify-center", className)}
      {...props}
    >
      <ResponsiveContainer width="100%" height="100%">
        {children}
      </ResponsiveContainer>
    </div>
  );
}

type TooltipPayloadItem = {
  name?: string | number;
  value?: number | string;
  color?: string;
  fill?: string;
  dataKey?: string | number;
};

export function ChartTooltipContent({
  active,
  payload,
  label,
  labelFormatter,
  valueFormatter,
}: {
  active?: boolean;
  payload?: TooltipPayloadItem[];
  label?: string | number;
  labelFormatter?: (label: string | number) => string;
  valueFormatter?: (value: number | string) => string;
}) {
  if (!active || !payload || payload.length === 0) return null;
  return (
    <div className="rounded-md border border-border bg-popover px-2.5 py-1.5 text-xs shadow-sm">
      {label !== undefined && label !== null ? (
        <div className="mb-1 text-muted-foreground">
          {labelFormatter ? labelFormatter(label) : label}
        </div>
      ) : null}
      {payload.map((item, i) => (
        <div key={i} className="flex items-center gap-2">
          <span
            className="size-2 rounded-sm"
            style={{ background: item.color || item.fill }}
          />
          <span className="font-medium tabular-nums text-foreground">
            {valueFormatter && item.value !== undefined
              ? valueFormatter(item.value)
              : item.value}
          </span>
        </div>
      ))}
    </div>
  );
}
