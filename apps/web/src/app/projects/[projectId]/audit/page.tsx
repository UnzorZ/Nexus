"use client";

import { useMemo, useState } from "react";
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
} from "@tanstack/react-table";
import { ArrowDownIcon, ArrowUpIcon, ChevronsUpDownIcon } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ClipboardCheckIcon } from "@/components/ui/clipboard-check";
import { SearchIcon } from "@/components/ui/search";
import { TriangleAlertIcon } from "@/components/ui/triangle-alert-icon";
import { Skeleton } from "@/components/ui/skeleton";
import { Stagger, tint } from "@/components/dashboard/anim";
import {
  EmptyState,
  MonoChip,
  PageHeader,
  Panel,
  StatTile,
  StatusBadge,
} from "@/components/dashboard/shared";
import type { AuditEvent, AuditOutcome } from "@/features/audit/api";
import {
  actorMetaFor,
  formatRelativeTime,
  outcomeMeta,
} from "@/features/audit/display";
import { useProjectAudit } from "@/features/audit/useProjectAudit";
import { useProject } from "../useProject";

type RangeKey = "all" | "24h" | "7d" | "30d";

/** Rango → milisegundos hacia atrás desde ahora (para el `since` del servidor). */
const RANGE_MS: Record<RangeKey, number> = {
  all: 0,
  "24h": 86_400_000,
  "7d": 604_800_000,
  "30d": 2_592_000_000,
};

/** Definición de columnas (estable a nivel de módulo). El filtrado de
 * búsqueda/actor/resultado se hace aguas arriba (`filtered`); TanStack aporta el
 * modelo de columnas, de filas y el ordenamiento por cabecera. */
const columns: ColumnDef<AuditEvent>[] = [
  {
    id: "actor",
    header: "Actor",
    enableSorting: false,
    cell: ({ row }) => <ActorCell event={row.original} />,
  },
  {
    accessorKey: "action",
    header: "Action",
    cell: ({ row }) => <MonoChip>{row.original.action}</MonoChip>,
  },
  {
    id: "resource",
    header: "Resource",
    enableSorting: false,
    cell: ({ row }) => (
      <span className="text-xs text-muted-foreground">
        {row.original.resourceType ?? "—"}:
        <span className="text-foreground">{row.original.resourceId ?? "—"}</span>
      </span>
    ),
  },
  {
    accessorKey: "outcome",
    header: "Outcome",
    cell: ({ row }) => {
      const meta = outcomeMeta[row.original.outcome];
      return (
        <StatusBadge tone={meta.tone} dot>
          {meta.label}
        </StatusBadge>
      );
    },
  },
  {
    accessorKey: "ip",
    header: "IP",
    cell: ({ row }) => (
      <span className="font-mono text-[11px] text-muted-foreground">
        {row.original.ip ?? "—"}
      </span>
    ),
  },
  {
    accessorKey: "occurredAt",
    header: "Time",
    cell: ({ row }) => (
      <span className="whitespace-nowrap tabular-nums text-xs text-muted-foreground">
        {formatRelativeTime(row.original.occurredAt)}
      </span>
    ),
  },
];

function AuditLoading() {
  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Stagger className="mt-6">
        <Panel title="Audit log">
          <div className="flex flex-col gap-2">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        </Panel>
      </Stagger>
    </Stagger>
  );
}

export default function ProjectAuditPage() {
  const { project, loading: projectLoading, error: projectError } = useProject();

  const [query, setQuery] = useState("");
  const [actorFilter, setActorFilter] = useState<string>("all");
  const [outcomeFilter, setOutcomeFilter] = useState<AuditOutcome | "all">(
    "all",
  );
  const [range, setRange] = useState<RangeKey>("all");
  const [sorting, setSorting] = useState<SortingState>([
    { id: "occurredAt", desc: true },
  ]);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const { events, loading, error, refresh } = useProjectAudit(
    project?.id ?? "",
    RANGE_MS[range],
  );

  const name = project?.name ?? "...";
  const loadingState = projectLoading || (Boolean(project) && loading);

  const filtered = useMemo(() => {
    if (!events) return [];
    const q = query.trim().toLowerCase();
    return events.filter((e) => {
      if (actorFilter !== "all" && e.actorType !== actorFilter) return false;
      if (outcomeFilter !== "all" && e.outcome !== outcomeFilter) return false;
      if (!q) return true;
      return (
        e.action.toLowerCase().includes(q) ||
        (e.resourceType ?? "").toLowerCase().includes(q) ||
        (e.resourceId ?? "").toLowerCase().includes(q) ||
        (e.traceId ?? "").toLowerCase().includes(q) ||
        (e.actorId ?? "").toLowerCase().includes(q)
      );
    });
  }, [events, query, actorFilter, outcomeFilter]);

  const table = useReactTable({
    data: filtered,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const selected = events?.find((e) => e.id === selectedId) ?? null;
  const failures = filtered.filter((e) => e.outcome === "FAILURE").length;

  if (loadingState) {
    return <AuditLoading />;
  }

  if (projectError || error) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", name, "Audit"]}
          title="Audit"
          description=""
          projectId={project?.id}
        />
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              title="Could not load audit log"
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

  if (!project || !events) {
    return null;
  }

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, "Audit"]}
        title="Audit"
        description="Immutable trail of sensitive actions on this project. Metadata is kept small and never stores secrets, tokens, or raw keys."
        projectId={project.id}
        badge={
          <StatusBadge tone="amber" dot pulse>
            {filtered.length} events
          </StatusBadge>
        }
        actions={
          <Button variant="outline" onClick={() => refresh()}>
            Refresh
          </Button>
        }
      />

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Audit log"
          description="Key, member, role, permission, module and project lifecycle events."
        >
          <div className="mb-4 grid grid-cols-2 gap-3">
            <StatTile
              Icon={ClipboardCheckIcon}
              iconBg={tint.amber.bg}
              iconColor={tint.amber.text}
              label="Events"
              value={filtered.length}
              hint={range === "all" ? "Recent" : `Last ${range}`}
            />
            <StatTile
              Icon={TriangleAlertIcon}
              iconBg={tint.red.bg}
              iconColor={tint.red.text}
              label="Failures"
              value={failures}
              hint="Denied / failed"
            />
          </div>

          <div className="mb-3 flex flex-wrap items-center gap-2">
            <div className="relative min-w-[220px] flex-1">
              <SearchIcon
                size={15}
                className="pointer-events-none absolute top-1/2 left-2.5 -translate-y-1/2 text-muted-foreground"
              />
              <Input
                placeholder="Search action, resource, actor or trace…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="h-8 pl-8"
              />
            </div>
            <Select value={actorFilter} onValueChange={setActorFilter}>
              <SelectTrigger size="sm" className="w-44">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All actors</SelectItem>
                <SelectItem value="NEXUS_ACCOUNT">Nexus account</SelectItem>
                <SelectItem value="ANONYMOUS">Anonymous</SelectItem>
              </SelectContent>
            </Select>
            <Select
              value={outcomeFilter}
              onValueChange={(v) => setOutcomeFilter(v as AuditOutcome | "all")}
            >
              <SelectTrigger size="sm" className="w-36">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All outcomes</SelectItem>
                <SelectItem value="SUCCESS">Success</SelectItem>
                <SelectItem value="FAILURE">Failure</SelectItem>
              </SelectContent>
            </Select>
            <Select value={range} onValueChange={(v) => setRange(v as RangeKey)}>
              <SelectTrigger size="sm" className="w-32">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All time</SelectItem>
                <SelectItem value="24h">Last 24h</SelectItem>
                <SelectItem value="7d">Last 7 days</SelectItem>
                <SelectItem value="30d">Last 30 days</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {events.length === 0 ? (
            <EmptyState
              Icon={ClipboardCheckIcon}
              title="No audit events yet"
              description="Lifecycle and security events on this project will appear here as they happen."
            />
          ) : filtered.length === 0 ? (
            <EmptyState
              Icon={SearchIcon}
              title="No events match your filters"
              description="Try widening the time range or clearing the search."
            />
          ) : (
            <Table>
              <TableHeader>
                {table.getHeaderGroups().map((headerGroup) => (
                  <TableRow key={headerGroup.id}>
                    {headerGroup.headers.map((header) => {
                      const sortable = header.column.getCanSort();
                      return (
                        <TableHead
                          key={header.id}
                          className={
                            sortable ? "cursor-pointer select-none" : undefined
                          }
                          onClick={
                            sortable
                              ? header.column.getToggleSortingHandler()
                              : undefined
                          }
                          aria-sort={
                            header.column.getIsSorted() === "asc"
                              ? "ascending"
                              : header.column.getIsSorted() === "desc"
                                ? "descending"
                                : undefined
                          }
                        >
                          <span className="inline-flex items-center gap-1">
                            {flexRender(
                              header.column.columnDef.header,
                              header.getContext(),
                            )}
                            {sortable ? (
                              <SortIcon dir={header.column.getIsSorted()} />
                            ) : null}
                          </span>
                        </TableHead>
                      );
                    })}
                  </TableRow>
                ))}
              </TableHeader>
              <TableBody>
                {table.getRowModel().rows.map((row) => (
                  <TableRow
                    key={row.id}
                    onClick={() => setSelectedId(row.original.id)}
                    className="cursor-pointer"
                  >
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>
                        {flexRender(
                          cell.column.columnDef.cell,
                          cell.getContext(),
                        )}
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Panel>
      </Stagger>

      <Sheet
        open={selected !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedId(null);
        }}
      >
        <SheetContent className="flex w-full flex-col gap-0 sm:max-w-md">
          <SheetHeader className="gap-2">
            <SheetTitle className="flex items-center gap-2">
              <MonoChip>{selected?.action}</MonoChip>
            </SheetTitle>
            <SheetDescription>
              {selected?.resourceType}:{selected?.resourceId}
            </SheetDescription>
          </SheetHeader>

          {selected ? (
            <div className="-mx-6 flex-1 overflow-y-auto px-6">
              <dl className="grid grid-cols-2 gap-x-4 gap-y-3 border-t py-4 text-xs">
                <Detail label="Actor type" value={selected.actorType} />
                <Detail label="Actor" value={selected.actorId ?? "—"} mono />
                <Detail
                  label="Outcome"
                  value={outcomeMeta[selected.outcome].label}
                />
                <Detail label="IP address" value={selected.ip ?? "—"} mono />
                <Detail label="Trace ID" value={selected.traceId ?? "—"} mono />
                <Detail
                  label="Time"
                  value={formatRelativeTime(selected.occurredAt)}
                />
              </dl>

              <div className="border-t py-4">
                <p className="mb-1 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                  User agent
                </p>
                <p className="break-all font-mono text-[11px] text-muted-foreground">
                  {selected.userAgent ?? "—"}
                </p>
              </div>

              <div className="border-t py-4">
                <p className="mb-2 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                  Metadata
                </p>
                <pre className="overflow-x-auto rounded-md bg-muted p-3 font-mono text-[11px] leading-relaxed text-muted-foreground">
                  {selected.metadata
                    ? JSON.stringify(selected.metadata, null, 2)
                    : "—"}
                </pre>
                <p className="mt-2 text-[11px] leading-relaxed text-muted-foreground">
                  Payloads are kept small on purpose — never full keys, passwords
                  or tokens.
                </p>
              </div>
            </div>
          ) : null}
        </SheetContent>
      </Sheet>
    </Stagger>
  );
}

function ActorCell({ event }: { event: AuditEvent }) {
  const actor = actorMetaFor(event.actorType);
  return (
    <div className="flex items-center gap-2">
      <div
        className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-md ${actor.chipBg}`}
      >
        <actor.Icon size={13} className={actor.chipColor} />
      </div>
      <div className="flex flex-col">
        <span className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
          {event.actorType}
        </span>
        <span className="max-w-[160px] truncate text-xs text-foreground">
          {event.actorId ? shortId(event.actorId) : "—"}
        </span>
      </div>
    </div>
  );
}

function SortIcon({ dir }: { dir: false | "asc" | "desc" }) {
  if (dir === "asc") {
    return <ArrowUpIcon size={12} className="text-muted-foreground" />;
  }
  if (dir === "desc") {
    return <ArrowDownIcon size={12} className="text-muted-foreground" />;
  }
  return <ChevronsUpDownIcon size={12} className="text-muted-foreground/60" />;
}

function shortId(id: string): string {
  return id.length > 8 ? `${id.slice(0, 8)}…` : id;
}

function Detail({
  label,
  value,
  mono,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-muted-foreground">{label}</dt>
      <dd
        className={
          mono
            ? "break-all font-mono text-foreground"
            : "break-words text-foreground"
        }
      >
        {value}
      </dd>
    </div>
  );
}
