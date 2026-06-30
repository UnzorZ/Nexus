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
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
import { useProjectAudit } from "@/features/audit/queries";
import { toMessage } from "@/lib/api/errors";
import { useProject } from "../useProject";

type RangeKey = "all" | "24h" | "7d" | "30d";

/** Rango → milisegundos hacia atrás desde ahora (para el `since` del servidor). */
const RANGE_MS: Record<RangeKey, number> = {
  all: 0,
  "24h": 86_400_000,
  "7d": 604_800_000,
  "30d": 2_592_000_000,
};

/** Módulo = namespace de la acción (p. ej. "member.invited" → "member"). */
function moduleOf(action: string): string {
  const i = action.indexOf(".");
  return i > 0 ? action.slice(0, i) : action;
}

/** Definición de columnas (estable a nivel de módulo) con tamaños para el resize.
 * El filtrado se hace aguas arriba (`filtered`); TanStack aporta columnas, filas,
 * ordenamiento por cabecera y resize de columnas. */
const columns: ColumnDef<AuditEvent>[] = [
  {
    id: "actor",
    header: "Actor",
    enableSorting: false,
    size: 200,
    minSize: 130,
    cell: ({ row }) => <ActorCell event={row.original} />,
  },
  {
    accessorKey: "action",
    header: "Action",
    size: 150,
    minSize: 90,
    cell: ({ row }) => <MonoChip>{row.original.action}</MonoChip>,
  },
  {
    id: "resource",
    header: "Resource",
    enableSorting: false,
    size: 180,
    minSize: 110,
    cell: ({ row }) => (
      <span className="block truncate text-xs text-muted-foreground">
        {row.original.resourceType ?? "—"}:
        <span className="text-foreground">{row.original.resourceId ?? "—"}</span>
      </span>
    ),
  },
  {
    accessorKey: "outcome",
    header: "Outcome",
    size: 120,
    minSize: 90,
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
    size: 130,
    minSize: 90,
    cell: ({ row }) => (
      <span className="font-mono text-[11px] text-muted-foreground">
        {row.original.ip ?? "—"}
      </span>
    ),
  },
  {
    accessorKey: "occurredAt",
    header: "Time",
    size: 130,
    minSize: 90,
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
  const [moduleFilter, setModuleFilter] = useState<string>("all");
  const [outcomeFilter, setOutcomeFilter] = useState<AuditOutcome | "all">(
    "all",
  );
  const [range, setRange] = useState<RangeKey>("all");
  const [sorting, setSorting] = useState<SortingState>([
    { id: "occurredAt", desc: true },
  ]);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const {
    data: events,
    isLoading: loading,
    error: auditError,
    refetch,
  } = useProjectAudit(project?.id ?? "", RANGE_MS[range]);
  const error = auditError ? toMessage(auditError) : null;
  const refresh = () => refetch();

  const name = project?.name ?? "...";
  const loadingState = projectLoading || (Boolean(project) && loading);

  /** Módulos (namespaces de acción) presentes en el lote cargado, para el filtro. */
  const modules = useMemo(() => {
    if (!events) return [];
    const set = new Set<string>();
    for (const e of events) {
      const m = moduleOf(e.action);
      if (m) set.add(m);
    }
    return [...set].sort();
  }, [events]);

  const filtered = useMemo(() => {
    if (!events) return [];
    const q = query.trim().toLowerCase();
    return events.filter((e) => {
      if (actorFilter !== "all" && e.actorType !== actorFilter) return false;
      if (moduleFilter !== "all" && moduleOf(e.action) !== moduleFilter)
        return false;
      if (outcomeFilter !== "all" && e.outcome !== outcomeFilter) return false;
      if (!q) return true;
      return (
        e.action.toLowerCase().includes(q) ||
        (e.resourceType ?? "").toLowerCase().includes(q) ||
        (e.resourceId ?? "").toLowerCase().includes(q) ||
        (e.traceId ?? "").toLowerCase().includes(q) ||
        (e.actorId ?? "").toLowerCase().includes(q) ||
        (e.actorDisplayName ?? "").toLowerCase().includes(q) ||
        (e.actorEmail ?? "").toLowerCase().includes(q)
      );
    });
  }, [events, query, actorFilter, moduleFilter, outcomeFilter]);

  const table = useReactTable({
    data: filtered,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    enableColumnResizing: true,
    columnResizeMode: "onChange",
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
            <Select value={moduleFilter} onValueChange={setModuleFilter}>
              <SelectTrigger size="sm" className="w-40">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All modules</SelectItem>
                {modules.map((m) => (
                  <SelectItem key={m} value={m}>
                    {m}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
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
            <Table
              style={{
                width: table.getTotalSize(),
                minWidth: "100%",
                tableLayout: "fixed",
              }}
            >
              <TableHeader>
                {table.getHeaderGroups().map((headerGroup) => (
                  <TableRow key={headerGroup.id}>
                    {headerGroup.headers.map((header) => {
                      const sortable = header.column.getCanSort();
                      const resizing = header.column.getIsResizing();
                      return (
                        <TableHead
                          key={header.id}
                          style={{
                            width: header.getSize(),
                            position: "relative",
                          }}
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
                          {header.column.getCanResize() ? (
                            <div
                              role="separator"
                              aria-orientation="vertical"
                              onMouseDown={header.getResizeHandler()}
                              onTouchStart={header.getResizeHandler()}
                              onClick={(e) => e.stopPropagation()}
                              className={`absolute right-0 top-0 h-full w-1.5 cursor-col-resize touch-none select-none bg-transparent transition-colors hover:bg-border ${
                                resizing ? "bg-primary" : ""
                              }`}
                            />
                          ) : null}
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
                      <TableCell
                        key={cell.id}
                        style={{ width: cell.column.getSize() }}
                      >
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
          <p className="mt-2 text-[11px] text-muted-foreground">
            Drag a column header edge to resize · click a row for details.
          </p>
        </Panel>
      </Stagger>

      <Dialog
        open={selected !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedId(null);
        }}
      >
        <DialogContent className="sm:max-w-lg">
          <DialogHeader className="gap-2">
            <DialogTitle className="flex items-center gap-2">
              <MonoChip>{selected?.action}</MonoChip>
              {selected ? (
                <StatusBadge tone={outcomeMeta[selected.outcome].tone} dot>
                  {outcomeMeta[selected.outcome].label}
                </StatusBadge>
              ) : null}
            </DialogTitle>
            <DialogDescription>
              {selected?.resourceType}:{selected?.resourceId}
            </DialogDescription>
          </DialogHeader>

          {selected ? <AuditDetail event={selected} /> : null}
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}

function AuditDetail({ event }: { event: AuditEvent }) {
  const actor = actorMetaFor(event.actorType);
  const hasAccount =
    event.actorDisplayName != null || event.actorEmail != null;
  return (
    <div className="flex flex-col gap-4">
      {/* Actor / usuario */}
      <div className="rounded-lg border p-3">
        <p className="mb-2 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
          Actor
        </p>
        <div className="flex items-center gap-2.5">
          <div
            className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-md ${actor.chipBg}`}
          >
            <actor.Icon size={15} className={actor.chipColor} />
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-foreground">
              {event.actorDisplayName ??
                event.actorEmail ??
                (event.actorId ? shortId(event.actorId) : "—")}
            </p>
            <p className="truncate text-xs text-muted-foreground">
              {event.actorEmail ?? actor.label}
            </p>
          </div>
        </div>
        {hasAccount || event.actorId ? (
          <dl className="mt-3 grid grid-cols-1 gap-y-1 text-xs">
            {hasAccount ? (
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">Account ID</dt>
                <dd className="break-all font-mono text-foreground">
                  {event.actorId ?? "—"}
                </dd>
              </div>
            ) : null}
            <div className="flex justify-between gap-3">
              <dt className="text-muted-foreground">Type</dt>
              <dd className="text-foreground">{event.actorType}</dd>
            </div>
          </dl>
        ) : null}
      </div>

      {/* Detalle del evento */}
      <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-xs">
        <Detail label="IP address" value={event.ip ?? "—"} mono />
        <Detail label="Trace ID" value={event.traceId ?? "—"} mono />
        <Detail label="Resource" value={`${event.resourceType ?? "—"}:${event.resourceId ?? "—"}`} />
        <Detail label="Time" value={formatRelativeTime(event.occurredAt)} />
      </dl>

      <div>
        <p className="mb-1 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
          User agent
        </p>
        <p className="break-all font-mono text-[11px] text-muted-foreground">
          {event.userAgent ?? "—"}
        </p>
      </div>

      <div>
        <p className="mb-2 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
          Metadata
        </p>
        <pre className="overflow-x-auto rounded-md bg-muted p-3 font-mono text-[11px] leading-relaxed text-muted-foreground">
          {event.metadata ? JSON.stringify(event.metadata, null, 2) : "—"}
        </pre>
        <p className="mt-2 text-[11px] leading-relaxed text-muted-foreground">
          Payloads are kept small on purpose — never full keys, passwords or
          tokens.
        </p>
      </div>
    </div>
  );
}

function ActorCell({ event }: { event: AuditEvent }) {
  const actor = actorMetaFor(event.actorType);
  const name = event.actorDisplayName ?? event.actorEmail;
  const primary =
    name ?? (event.actorId ? shortId(event.actorId) : "—");
  return (
    <div className="flex items-center gap-2">
      <div
        className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-md ${actor.chipBg}`}
      >
        <actor.Icon size={13} className={actor.chipColor} />
      </div>
      <div className="flex min-w-0 flex-col">
        <span className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
          {actor.label}
        </span>
        <span className="max-w-[180px] truncate text-xs text-foreground">
          {primary}
        </span>
        {event.actorEmail && event.actorDisplayName ? (
          <span className="max-w-[180px] truncate text-[10px] text-muted-foreground">
            {event.actorEmail}
          </span>
        ) : null}
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
