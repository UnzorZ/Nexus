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
import {
  ArrowDownIcon,
  ArrowUpIcon,
  ChevronsUpDownIcon,
  Filter,
  X,
} from "lucide-react";
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
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
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
import type { AuditEvent, Severity } from "@/features/audit/api";
import {
  actorMetaFor,
  formatRelativeTime,
  severityMeta,
} from "@/features/audit/display";
import { useProjectAudit } from "@/features/audit/queries";
import { colorFor, initials } from "@/lib/account";
import { toMessage } from "@/lib/api/errors";
import { useProject } from "../useProject";

type RangeKey = "all" | "24h" | "7d" | "30d";

const RANGE_MS: Record<RangeKey, number> = {
  all: 0,
  "24h": 86_400_000,
  "7d": 604_800_000,
  "30d": 2_592_000_000,
};

const ACTOR_LABELS: Record<string, string> = {
  NEXUS_ACCOUNT: "Nexus account",
  ANONYMOUS: "Anonymous",
};

const SEVERITIES: Severity[] = ["INFO", "WARNING", "MODERATE", "CRITICAL"];

function moduleOf(action: string): string {
  const i = action.indexOf(".");
  return i > 0 ? action.slice(0, i) : action;
}

function actorKindLabel(event: AuditEvent): string {
  if (event.actorType === "NEXUS_ACCOUNT") {
    return event.actorAdmin ? "Admin" : "Normal";
  }
  return ACTOR_LABELS[event.actorType] ?? event.actorType;
}

const columns: ColumnDef<AuditEvent>[] = [
  {
    id: "actor",
    header: "Actor",
    enableSorting: false,
    size: 210,
    minSize: 140,
    cell: ({ row }) => <ActorCell event={row.original} />,
  },
  {
    accessorKey: "action",
    header: "Action",
    size: 160,
    minSize: 100,
    cell: ({ row }) => <MonoChip>{row.original.action}</MonoChip>,
  },
  {
    id: "resource",
    header: "Resource",
    enableSorting: false,
    size: 190,
    minSize: 120,
    cell: ({ row }) => (
      <span className="block truncate text-xs text-muted-foreground">
        {row.original.resourceType ?? "—"}:
        <span className="text-foreground">{row.original.resourceId ?? "—"}</span>
      </span>
    ),
  },
  {
    accessorKey: "severity",
    header: "Severity",
    size: 130,
    minSize: 100,
    cell: ({ row }) => {
      const meta = severityMeta[row.original.severity];
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
  const [severityFilter, setSeverityFilter] = useState<Severity | "all">("all");
  const [ipFilter, setIpFilter] = useState<string>("all");
  const [range, setRange] = useState<RangeKey>("all");
  const [sorting, setSorting] = useState<SortingState>([
    { id: "occurredAt", desc: true },
  ]);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const audit = useProjectAudit(project?.id ?? "", RANGE_MS[range]);
  const events = useMemo(
    () => audit.data?.pages.flatMap((p) => p.items) ?? null,
    [audit.data],
  );
  const totalElements = audit.data?.pages[0]?.totalElements ?? 0;
  const loading = audit.isLoading;
  const error = audit.error ? toMessage(audit.error) : null;
  const refresh = () => audit.refetch();

  const name = project?.name ?? "...";
  const loadingState = projectLoading || (Boolean(project) && loading);

  const modules = useMemo(() => {
    if (!events) return [];
    const set = new Set<string>();
    for (const e of events) {
      const m = moduleOf(e.action);
      if (m) set.add(m);
    }
    return [...set].sort();
  }, [events]);

  const actorTypes = useMemo(() => {
    if (!events) return [];
    return [...new Set(events.map((e) => e.actorType))].sort();
  }, [events]);

  const ips = useMemo(() => {
    if (!events) return [];
    return [...new Set(events.map((e) => e.ip).filter(Boolean) as string[])].sort();
  }, [events]);

  const filtered = useMemo(() => {
    if (!events) return [];
    const q = query.trim().toLowerCase();
    return events.filter((e) => {
      if (actorFilter !== "all" && e.actorType !== actorFilter) return false;
      if (moduleFilter !== "all" && moduleOf(e.action) !== moduleFilter)
        return false;
      if (severityFilter !== "all" && e.severity !== severityFilter)
        return false;
      if (ipFilter !== "all" && e.ip !== ipFilter) return false;
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
  }, [events, query, actorFilter, moduleFilter, severityFilter, ipFilter]);

  // TanStack Table devuelve funciones no memoizables por diseño; la regla
  // react-hooks/incompatible-library lo marca como falso positivo (no es un
  // bug de este código). Es el patrón establecido del data layer del repo.
  // eslint-disable-next-line react-hooks/incompatible-library
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
  const notable = filtered.filter((e) => e.severity !== "INFO").length;

  function applyFilter(value: string) {
    setQuery(value);
    setSelectedId(null);
  }

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
              hint={
                audit.hasNextPage
                  ? `${totalElements} total (loaded ${events.length})`
                  : range === "all"
                    ? "Recent"
                    : `Last ${range}`
              }
            />
            <StatTile
              Icon={TriangleAlertIcon}
              iconBg={tint.red.bg}
              iconColor={tint.red.text}
              label="Notable"
              value={notable}
              hint="Warning or higher"
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
                className="h-8 pl-8 pr-8"
              />
              {query ? (
                <button
                  type="button"
                  onClick={() => setQuery("")}
                  aria-label="Clear search"
                  className="absolute top-1/2 right-2 inline-flex h-4 w-4 -translate-y-1/2 items-center justify-center rounded-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                >
                  <X size={13} />
                </button>
              ) : null}
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
                {actorTypes.map((t) => (
                  <SelectItem key={t} value={t}>
                    {ACTOR_LABELS[t] ?? t}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select
              value={severityFilter}
              onValueChange={(v) => setSeverityFilter(v as Severity | "all")}
            >
              <SelectTrigger size="sm" className="w-36">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All severities</SelectItem>
                {SEVERITIES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {severityMeta[s].label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={ipFilter} onValueChange={setIpFilter}>
              <SelectTrigger size="sm" className="w-36">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All IPs</SelectItem>
                {ips.map((ip) => (
                  <SelectItem key={ip} value={ip}>
                    {ip}
                  </SelectItem>
                ))}
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
                              className={`absolute right-0 top-0 h-full cursor-col-resize touch-none select-none bg-border/60 transition-all hover:bg-primary hover:w-2 ${
                                resizing ? "w-2 bg-primary" : "w-0.5"
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

          <div className="mt-3 flex items-center justify-end gap-3 text-[11px] text-muted-foreground">
            {audit.hasNextPage ? (
              <Button
                variant="outline"
                size="sm"
                disabled={audit.isFetchingNextPage}
                onClick={() => audit.fetchNextPage()}
              >
                {audit.isFetchingNextPage ? "Loading…" : "Load more"}
              </Button>
            ) : (
              <span>{events.length} loaded</span>
            )}
          </div>
        </Panel>
      </Stagger>

      <Dialog
        open={selected !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedId(null);
        }}
      >
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader className="gap-2">
            <DialogTitle className="flex flex-wrap items-center gap-2">
              <MonoChip>{selected?.action}</MonoChip>
              <FilterButton
                label="Filter by type"
                onClick={() => selected && applyFilter(selected.action)}
              />
              {selected ? (
                <StatusBadge tone={severityMeta[selected.severity].tone} dot>
                  {severityMeta[selected.severity].label}
                </StatusBadge>
              ) : null}
            </DialogTitle>
            <DialogDescription>
              {selected?.resourceType}:{selected?.resourceId}
            </DialogDescription>
          </DialogHeader>

          {selected ? (
            <AuditDetail event={selected} onFilter={applyFilter} />
          ) : null}
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}

function AuditDetail({
  event,
  onFilter,
}: {
  event: AuditEvent;
  onFilter: (value: string) => void;
}) {
  const actor = actorMetaFor(event.actorType);
  const name = event.actorDisplayName ?? event.actorEmail;
  const seed = event.actorEmail ?? event.actorId ?? event.actorType;
  return (
    <div className="flex min-h-[320px] flex-col gap-4">
      <div className="rounded-lg border p-3">
        <div className="flex items-center justify-between">
          <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
            Actor
          </p>
          {name ? (
            <FilterButton
              label="Filter by user"
              onClick={() =>
                onFilter(event.actorEmail ?? event.actorDisplayName ?? "")
              }
            />
          ) : null}
        </div>
        <div className="mt-2 flex items-center gap-2.5">
          <Avatar className="size-9">
            <AvatarFallback
              className={`size-9 text-xs font-semibold text-white ${colorFor(seed)}`}
            >
              {name ? (
                initials(name)
              ) : (
                <actor.Icon size={16} className={actor.chipColor} />
              )}
            </AvatarFallback>
          </Avatar>
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-foreground">
              {name ?? "—"}
            </p>
            <p className="truncate text-xs text-muted-foreground">
              {actorKindLabel(event)}
              {event.actorEmail ? ` · ${event.actorEmail}` : ""}
            </p>
          </div>
        </div>
      </div>

      <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-xs">
        <Detail label="IP address" value={event.ip ?? "—"} mono />
        <div className="flex flex-col gap-0.5">
          <dt className="text-muted-foreground">Trace ID</dt>
          <dd className="flex items-center gap-1.5">
            <span className="break-all font-mono text-foreground">
              {event.traceId ?? "—"}
            </span>
            {event.traceId ? (
              <FilterButton onClick={() => onFilter(event.traceId ?? "")} />
            ) : null}
          </dd>
        </div>
        <Detail
          label="Resource"
          value={`${event.resourceType ?? "—"}:${event.resourceId ?? "—"}`}
        />
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
        <MetadataList data={event.metadata} />
      </div>
    </div>
  );
}

function MetadataList({ data }: { data: Record<string, unknown> | null }) {
  const entries = data ? Object.entries(data) : [];
  if (entries.length === 0) {
    return <p className="text-xs text-muted-foreground">—</p>;
  }
  return (
    <dl className="divide-y overflow-hidden rounded-md border">
      {entries.map(([k, v]) => (
        <div
          key={k}
          className="flex items-start justify-between gap-3 px-3 py-2 text-xs"
        >
          <dt className="shrink-0 font-medium text-muted-foreground">{k}</dt>
          <dd className="min-w-0 text-right">
            <MetadataValue value={v} />
          </dd>
        </div>
      ))}
    </dl>
  );
}

function MetadataValue({ value }: { value: unknown }) {
  if (value == null) {
    return <span className="text-muted-foreground">—</span>;
  }
  if (typeof value === "boolean") {
    return (
      <StatusBadge tone={value ? "emerald" : "slate"}>
        {String(value)}
      </StatusBadge>
    );
  }
  if (Array.isArray(value)) {
    return (
      <span className="flex flex-wrap justify-end gap-1">
        {value.map((x, i) => (
          <MonoChip key={i}>{String(x)}</MonoChip>
        ))}
      </span>
    );
  }
  if (typeof value === "object") {
    return (
      <span className="break-all font-mono text-[11px] text-muted-foreground">
        {JSON.stringify(value)}
      </span>
    );
  }
  const s = String(value);
  const isUuidLike = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
    s,
  );
  return isUuidLike ? (
    <span className="break-all font-mono text-[11px] text-foreground">{s}</span>
  ) : (
    <span className="break-words text-foreground">{s}</span>
  );
}

function ActorCell({ event }: { event: AuditEvent }) {
  const actor = actorMetaFor(event.actorType);
  const name = event.actorDisplayName ?? event.actorEmail;
  const seed = event.actorEmail ?? event.actorId ?? event.actorType;
  return (
    <div className="flex items-center gap-2">
      <Avatar className="size-6">
        <AvatarFallback
          className={`size-6 text-[10px] font-semibold text-white ${colorFor(seed)}`}
        >
          {name ? (
            initials(name)
          ) : (
            <actor.Icon size={12} className={actor.chipColor} />
          )}
        </AvatarFallback>
      </Avatar>
      <div className="flex min-w-0 flex-col">
        <span className="max-w-[180px] truncate text-xs text-foreground">
          {name ?? (event.actorId ? shortId(event.actorId) : actor.label)}
        </span>
        <span className="text-[10px] uppercase tracking-wide text-muted-foreground">
          {actorKindLabel(event)}
        </span>
      </div>
    </div>
  );
}

function FilterButton({
  onClick,
  label,
}: {
  onClick: () => void;
  label?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={label ?? "Filter by this"}
      className="inline-flex shrink-0 items-center gap-1 rounded-md border border-border px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
    >
      <Filter size={11} />
      {label ? <span>{label}</span> : null}
    </button>
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
