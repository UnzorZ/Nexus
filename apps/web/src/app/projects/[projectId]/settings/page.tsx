"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Archive, ArchiveRestore, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { TriangleAlertIcon } from "@/components/ui/triangle-alert-icon";
import { Stagger } from "@/components/dashboard/anim";
import {
  EmptyState,
  MonoChip,
  PageHeader,
  Panel,
  StatusBadge,
  type Tone,
} from "@/components/dashboard/shared";
import { type ProjectDetails } from "@/features/projects/api";
import {
  useArchiveProject,
  useRestoreProject,
  useUpdateProject,
} from "@/features/projects/queries";
import { toFieldErrors, toMessage } from "@/lib/api/errors";
import { useProject } from "../useProject";

function formatInstant(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("es-ES", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function statusLabel(status: ProjectDetails["status"]): string {
  return status.charAt(0) + status.slice(1).toLowerCase();
}

function statusTone(status: ProjectDetails["status"]): Tone {
  if (status === "ACTIVE") return "emerald";
  if (status === "SUSPENDED") return "amber";
  return "slate";
}

function isValidPublicBaseUrl(value: string): boolean {
  const trimmed = value.trim();
  if (!trimmed) return true;
  try {
    const url = new URL(trimmed);
    return (
      (url.protocol === "http:" || url.protocol === "https:") &&
      url.host.length > 0
    );
  } catch {
    return false;
  }
}

type FormState = {
  name: string;
  description: string;
  publicBaseUrl: string;
};

function formFromProject(project: ProjectDetails): FormState {
  return {
    name: project.name,
    description: project.description ?? "",
    publicBaseUrl: project.publicBaseUrl ?? "",
  };
}

function SettingsLoading() {
  return (
    <Stagger root className="mx-auto flex w-full max-w-4xl flex-1 flex-col">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Stagger className="mt-6">
        <Panel title="General">
          <div className="flex flex-col gap-4">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-24 w-full" />
          </div>
        </Panel>
      </Stagger>
    </Stagger>
  );
}

const UPDATE_MESSAGES = {
  permission: "You don't have permission to change this project.",
  notFound: "This project no longer exists.",
};
const ARCHIVE_MESSAGES = {
  permission: "You don't have permission to archive this project.",
  notFound: "This project no longer exists.",
};
const RESTORE_MESSAGES = {
  permission: "You don't have permission to restore this project.",
  notFound: "This project no longer exists.",
};

export default function ProjectSettingsPage() {
  const router = useRouter();
  const { project, loading, error, refresh } = useProject();

  const updateM = useUpdateProject(project?.id ?? "");
  const archiveM = useArchiveProject(project?.id ?? "");
  const restoreM = useRestoreProject(project?.id ?? "");

  const [form, setForm] = useState<FormState>({ name: "", description: "", publicBaseUrl: "" });
  const [savedFlash, setSavedFlash] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [confirmSlug, setConfirmSlug] = useState("");

  const saving = updateM.isPending;
  const archiving = archiveM.isPending;
  const restoring = restoreM.isPending;
  const fieldErrors = toFieldErrors(updateM.error);
  const submitError =
    updateM.error && Object.keys(fieldErrors).length === 0
      ? toMessage(updateM.error, UPDATE_MESSAGES)
      : null;
  const archiveError = archiveM.error
    ? toMessage(archiveM.error, ARCHIVE_MESSAGES)
    : null;
  const restoreError = restoreM.error
    ? toMessage(restoreM.error, RESTORE_MESSAGES)
    : null;

  const savedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!project) return;
    // Reset del formulario cuando cambia el proyecto (patrón establecido en el repo).
    // Dependencias reducidas a id/updatedAt a propósito: resetear sólo en estos
    // cambios evita descartar ediciones en curso cuando el cache refresca la
    // referencia del objeto `project` sin cambiar su contenido.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setForm(formFromProject(project));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [project?.id, project?.updatedAt]);

  useEffect(() => {
    return () => {
      if (savedTimerRef.current) clearTimeout(savedTimerRef.current);
    };
  }, []);

  const flashSaved = useCallback(() => {
    setSavedFlash(true);
    if (savedTimerRef.current) clearTimeout(savedTimerRef.current);
    savedTimerRef.current = setTimeout(() => setSavedFlash(false), 2000);
  }, []);

  const baseline = useMemo(
    () => (project ? formFromProject(project) : null),
    [project],
  );

  const isDirty = useMemo(() => {
    if (!baseline) return false;
    return (
      form.name !== baseline.name ||
      form.description !== baseline.description ||
      form.publicBaseUrl !== baseline.publicBaseUrl
    );
  }, [form, baseline]);

  const clientValid = useMemo(() => {
    const name = form.name.trim();
    if (!name || name.length > 120) return false;
    if (form.description.length > 1000) return false;
    return isValidPublicBaseUrl(form.publicBaseUrl);
  }, [form]);

  const canManage = project?.canManage ?? false;
  const canDelete = project?.canDelete ?? false;

  async function onSave() {
    if (!project) return;
    updateM.reset();
    try {
      await updateM.mutateAsync({
        name: form.name.trim(),
        description: form.description.trim() ? form.description.trim() : null,
        publicBaseUrl: form.publicBaseUrl.trim()
          ? form.publicBaseUrl.trim()
          : null,
      });
      // updateM.onSuccess invalida la ficha del proyecto → el header y el
      // formulario se refrescan solos; flashSaved confirma el guardado.
      flashSaved();
    } catch {
      /* submitError/fieldErrors se muestran vía updateM.error */
    }
  }

  async function onArchive() {
    if (!project) return;
    archiveM.reset();
    try {
      await archiveM.mutateAsync();
      router.push("/projects");
    } catch {
      /* archiveError via archiveM.error */
    }
  }

  async function onRestore() {
    if (!project) return;
    restoreM.reset();
    try {
      // restoreM.onSuccess invalida la ficha → el estado y el badge se
      // actualizan solos.
      await restoreM.mutateAsync();
    } catch {
      /* restoreError via restoreM.error */
    }
  }

  if (loading) {
    return <SettingsLoading />;
  }

  if (error) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-4xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", "Project settings"]}
          title="Project settings"
          description=""
        />
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              title="Could not load project"
              description={error}
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

  if (!project) {
    return null;
  }

  const slug = project.slug;
  const name = project.name;

  return (
    <Stagger root className="mx-auto flex w-full max-w-4xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, "Project settings"]}
        title="Project settings"
        description="Display metadata and identity for this project boundary."
        projectId={project.id}
        badge={
          <StatusBadge
            tone={statusTone(project.status)}
            dot
            pulse={project.status === "ACTIVE"}
          >
            {statusLabel(project.status)}
          </StatusBadge>
        }
        actions={
          <Button
            onClick={onSave}
            disabled={!canManage || !isDirty || !clientValid || saving}
          >
            {saving ? (
              <>
                <span className="nexus-live relative h-2 w-2 rounded-full bg-current" />
                Saving…
              </>
            ) : savedFlash ? (
              <>
                <Check size={14} />
                Saved
              </>
            ) : (
              "Save changes"
            )}
          </Button>
        }
      />

      {submitError ? (
        <p className="mt-4 text-sm text-destructive">{submitError}</p>
      ) : null}

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="General"
          description="Display metadata for this project boundary."
        >
          <div className="flex flex-col gap-4">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="proj-name">Name</Label>
                <Input
                  id="proj-name"
                  value={form.name}
                  onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                  readOnly={!canManage}
                  aria-invalid={Boolean(fieldErrors.name)}
                />
                {fieldErrors.name ? (
                  <p className="text-xs text-destructive">{fieldErrors.name}</p>
                ) : null}
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="proj-slug">Slug</Label>
                <div className="flex items-center gap-1">
                  <Input
                    id="proj-slug"
                    value={slug}
                    readOnly
                    className="font-mono"
                  />
                  <StatusBadge tone="slate">locked</StatusBadge>
                </div>
                <p className="text-[11px] text-muted-foreground">
                  Slugs are immutable after creation.
                </p>
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between">
                <Label htmlFor="proj-desc">Description</Label>
                <span
                  className={`text-[11px] tabular-nums ${
                    form.description.length > 1000
                      ? "text-destructive"
                      : "text-muted-foreground"
                  }`}
                >
                  {form.description.length}/1000
                </span>
              </div>
              <Textarea
                id="proj-desc"
                rows={3}
                value={form.description}
                onChange={(e) =>
                  setForm((prev) => ({ ...prev, description: e.target.value }))
                }
                readOnly={!canManage}
                aria-invalid={Boolean(fieldErrors.description)}
              />
              {fieldErrors.description ? (
                <p className="text-xs text-destructive">{fieldErrors.description}</p>
              ) : null}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="proj-url">Public base URL</Label>
              <Input
                id="proj-url"
                value={form.publicBaseUrl}
                onChange={(e) =>
                  setForm((prev) => ({ ...prev, publicBaseUrl: e.target.value }))
                }
                readOnly={!canManage}
                className="font-mono"
                aria-invalid={Boolean(fieldErrors.publicBaseUrl)}
              />
              <p className="text-[11px] text-muted-foreground">
                Used for OAuth issuer and redirect URI defaults.
              </p>
              {fieldErrors.publicBaseUrl ? (
                <p className="text-xs text-destructive">{fieldErrors.publicBaseUrl}</p>
              ) : null}
            </div>
          </div>
        </Panel>

        <Panel title="Identity" description="Project metadata and timestamps.">
          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between gap-3">
              <span className="text-xs text-muted-foreground">Project ID</span>
              <MonoChip>{project.id}</MonoChip>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-xs text-muted-foreground">Status</span>
              <StatusBadge tone={statusTone(project.status)} dot={project.status === "ACTIVE"}>
                {statusLabel(project.status)}
              </StatusBadge>
            </div>
            <div className="flex items-center justify-between gap-3 border-t pt-3">
              <span className="text-xs text-muted-foreground">Created</span>
              <span className="text-xs text-foreground">{formatInstant(project.createdAt)}</span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-xs text-muted-foreground">Updated</span>
              <span className="text-xs text-foreground">{formatInstant(project.updatedAt)}</span>
            </div>
          </div>
        </Panel>

        <Panel
          title="Danger zone"
          description="Reversible actions. Require an active Owner."
          cardClassName="ring-destructive/30"
        >
          {project.status === "ARCHIVED" ? (
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-start gap-2.5">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
                  <ArchiveRestore size={16} />
                </div>
                <div>
                  <p className="text-sm font-medium">Restore this project</p>
                  <p className="text-xs text-muted-foreground">
                    Bring <strong>{name}</strong> back to an active state. Members
                    can use it again immediately.
                  </p>
                  {restoreError ? (
                    <p className="mt-1.5 text-xs text-destructive">{restoreError}</p>
                  ) : null}
                </div>
              </div>
              <Button
                onClick={onRestore}
                disabled={!canDelete || restoring}
                className="shrink-0"
              >
                {restoring ? (
                  <>
                    <span className="nexus-live relative h-2 w-2 rounded-full bg-current" />
                    Restoring…
                  </>
                ) : (
                  <>
                    <ArchiveRestore size={14} />
                    Restore project
                  </>
                )}
              </Button>
            </div>
          ) : (
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-start gap-2.5">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-red-500/10 text-red-600 dark:text-red-400">
                  <TriangleAlertIcon size={16} />
                </div>
                <div>
                  <p className="text-sm font-medium">Archive this project</p>
                  <p className="text-xs text-muted-foreground">
                    Archiving marks <strong>{name}</strong> as inactive. It stays
                    in your project list and can be restored later.
                  </p>
                </div>
              </div>
              <Button
                variant="destructive"
                onClick={() => setArchiveOpen(true)}
                disabled={!canDelete}
                className="shrink-0"
              >
                <Archive size={14} />
                Archive project
              </Button>
            </div>
          )}
        </Panel>
      </Stagger>

      <Dialog
        open={archiveOpen}
        onOpenChange={(open) => {
          setArchiveOpen(open);
          if (!open) {
            setConfirmSlug("");
            archiveM.reset();
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Archive {name}?</DialogTitle>
            <DialogDescription>
              This marks the project as inactive. Type the slug to confirm.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2 py-1">
            <Input
              placeholder={slug}
              value={confirmSlug}
              onChange={(e) => setConfirmSlug(e.target.value)}
              className="font-mono"
            />
            {archiveError ? (
              <p className="text-xs text-destructive">{archiveError}</p>
            ) : null}
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              variant="destructive"
              disabled={confirmSlug !== slug || archiving}
              onClick={onArchive}
            >
              {archiving ? "Archiving…" : "Archive project"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}
