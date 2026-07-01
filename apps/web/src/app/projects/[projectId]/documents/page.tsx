"use client";

import { useEffect, useState } from "react";
import { PlusIcon } from "@/components/ui/plus";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { DeleteIcon } from "@/components/ui/delete";
import { SettingsIcon } from "@/components/ui/settings";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { Stagger } from "@/components/dashboard/anim";
import {
  EmptyState,
  PageHeader,
  Panel,
  StatusBadge,
} from "@/components/dashboard/shared";
import {
  useCreateDocumentTemplate,
  useDeleteDocumentTemplate,
  useProjectDocumentRenders,
  useProjectDocumentTemplates,
  useUpdateDocumentTemplate,
} from "@/features/documents/queries";
import type { DocumentTemplate } from "@/features/documents/api";
import { toFieldErrors, toMessage } from "@/lib/api/errors";
import { useProject } from "../useProject";

const CONTENT_TYPES = ["text/plain", "text/html", "text/markdown", "application/json"];

function DocumentsLoading() {
  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Stagger className="mt-6">
        <Panel title="Templates">
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

type FormState = { name: string; contentType: string; templateBody: string };
const EMPTY_FORM: FormState = {
  name: "",
  contentType: "text/plain",
  templateBody: "",
};

const DOCUMENTS_MESSAGES = {
  permission: "You don't have permission to manage this project's templates.",
  forbidden: "Your session expired. Reload the page and try again.",
  notFound: "This template no longer exists.",
  codes: {},
};

export default function ProjectDocumentsPage() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const projectId = project?.id ?? "";

  const templatesQ = useProjectDocumentTemplates(projectId);
  const rendersQ = useProjectDocumentRenders(projectId);
  const createM = useCreateDocumentTemplate(projectId);
  const updateM = useUpdateDocumentTemplate(projectId);
  const deleteM = useDeleteDocumentTemplate(projectId);

  const templates = templatesQ.data ?? null;
  const renders = rendersQ.data ?? null;
  const loading = templatesQ.isLoading;
  const error = templatesQ.error ? toMessage(templatesQ.error) : null;
  const refresh = () => templatesQ.refetch();

  const canManage = project?.canManage ?? false;
  const name = project?.name ?? "...";
  const [editing, setEditing] = useState<DocumentTemplate | null | "new">(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [removeTarget, setRemoveTarget] = useState<DocumentTemplate | null>(null);

  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect */
    if (editing === "new") {
      setForm(EMPTY_FORM);
    } else if (editing) {
      setForm({
        name: editing.name,
        contentType: editing.contentType,
        templateBody: editing.templateBody,
      });
    }
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [editing]);

  const busy = createM.isPending || updateM.isPending || deleteM.isPending;
  const fieldErrors = toFieldErrors(createM.error ?? updateM.error);
  const actionError =
    createM.error ?? updateM.error ?? deleteM.error
      ? toMessage(createM.error ?? updateM.error ?? deleteM.error, DOCUMENTS_MESSAGES)
      : null;

  const loadingState = projectLoading || (Boolean(project) && loading);

  function resetActionErrors() {
    createM.reset();
    updateM.reset();
    deleteM.reset();
  }

  function openCreate() {
    resetActionErrors();
    setEditing("new");
  }

  function openEdit(template: DocumentTemplate) {
    resetActionErrors();
    setEditing(template);
  }

  async function onSubmit() {
    const body = {
      name: form.name.trim(),
      contentType: form.contentType.trim() || "text/plain",
      templateBody: form.templateBody,
    };
    if (!body.name || editing === null) return;
    resetActionErrors();
    try {
      if (editing === "new") {
        await createM.mutateAsync(body);
      } else {
        await updateM.mutateAsync({ templateId: editing.id, ...body });
      }
      setEditing(null);
    } catch {
      /* se muestra vía createM/updateM.error */
    }
  }

  if (loadingState) {
    return <DocumentsLoading />;
  }

  if (projectError || error) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", name, "Documents"]}
          title="Documents"
          description=""
          projectId={project?.id}
        />
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              title="Could not load templates"
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

  if (!project || !templates) {
    return null;
  }

  const formOpen = editing !== null;

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, "Documents"]}
        title="Documents"
        description={`Templated documents for ${name}. Apps render them via POST /api/v1/documents/render (scope documents:render) with {{variable}} substitution.`}
        projectId={project.id}
        badge={
          <StatusBadge tone="emerald" dot pulse>
            {templates.length} templates
          </StatusBadge>
        }
        actions={
          canManage ? (
            <Button onClick={openCreate}>
              <PlusIcon size={14} />
              New template
            </Button>
          ) : undefined
        }
      />

      {actionError ? (
        <p className="mt-4 text-sm text-destructive">{actionError}</p>
      ) : null}

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Templates"
          description="Use {{variable}} placeholders; apps supply values at render time."
        >
          {templates.length === 0 ? (
            <EmptyState
              Icon={SettingsIcon}
              title="No templates yet"
              description="Create your first template, e.g. a welcome email body with {{name}}."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Type</TableHead>
                  {canManage ? <TableHead className="w-8" /> : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {templates.map((template) => (
                  <TableRow key={template.id}>
                    <TableCell className="font-medium">{template.name}</TableCell>
                    <TableCell>
                      <code className="font-mono text-xs text-muted-foreground">
                        {template.contentType}
                      </code>
                    </TableCell>
                    {canManage ? (
                      <TableCell>
                        <DropdownMenu modal={false}>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              aria-label={`Actions for ${template.name}`}
                            >
                              <EllipsisIcon size={14} />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-40">
                            <DropdownMenuItem
                              disabled={busy}
                              onClick={() => openEdit(template)}
                            >
                              Edit
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              variant="destructive"
                              disabled={busy}
                              onClick={() => setRemoveTarget(template)}
                              className="text-destructive focus:text-destructive"
                            >
                              <DeleteIcon className="size-3.5" />
                              Delete
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    ) : null}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Panel>

        <Panel title="Recent renders" description="Last 50 documents rendered via the project API.">
          {!renders || renders.length === 0 ? (
            <EmptyState
              title="No renders yet"
              description="Rendered documents appear here once an app calls the render endpoint."
            />
          ) : (
            <div className="flex flex-col gap-3">
              {renders.map((render) => (
                <div
                  key={render.id}
                  className="rounded-lg border border-border bg-muted/30 p-3"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm font-medium">{render.templateName}</span>
                    <span className="text-[11px] text-muted-foreground">
                      {new Date(render.createdAt).toLocaleString()}
                    </span>
                  </div>
                  <pre className="mt-2 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-background p-2 font-mono text-xs">
                    {render.output}
                  </pre>
                </div>
              ))}
            </div>
          )}
        </Panel>
      </Stagger>

      {/* Create / edit dialog */}
      <Dialog
        open={formOpen}
        onOpenChange={(open) => {
          if (!open) setEditing(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editing === "new" ? "New template" : "Edit template"}
            </DialogTitle>
            <DialogDescription>
              {editing === "new"
                ? "Define a document template with {{variable}} placeholders."
                : "Update the template. The name can't change."}
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="doc-name">Name</Label>
              <Input
                id="doc-name"
                placeholder="welcome"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                disabled={editing !== "new"}
                aria-invalid={Boolean(fieldErrors.name)}
              />
              {fieldErrors.name ? (
                <p className="text-xs text-destructive">{fieldErrors.name}</p>
              ) : null}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Content type</Label>
              <div className="flex flex-wrap gap-1.5">
                {CONTENT_TYPES.map((ct) => (
                  <Button
                    key={ct}
                    type="button"
                    variant={form.contentType === ct ? "default" : "outline"}
                    size="sm"
                    onClick={() => setForm((f) => ({ ...f, contentType: ct }))}
                  >
                    {ct}
                  </Button>
                ))}
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="doc-body">Template body</Label>
              <Textarea
                id="doc-body"
                placeholder={"Hello {{name}}!"}
                value={form.templateBody}
                onChange={(e) =>
                  setForm((f) => ({ ...f, templateBody: e.target.value }))
                }
                rows={6}
                className="font-mono text-xs"
              />
            </div>
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              onClick={onSubmit}
              disabled={busy || !form.name.trim() || !form.templateBody}
            >
              {busy ? "Saving…" : editing === "new" ? "Create" : "Save changes"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Remove confirmation */}
      <Dialog
        open={removeTarget !== null}
        onOpenChange={(open) => {
          if (!open) setRemoveTarget(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete {removeTarget?.name}?</DialogTitle>
            <DialogDescription>
              The template will be removed. Past renders keep their output.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              variant="destructive"
              disabled={busy}
              onClick={async () => {
                if (removeTarget) {
                  resetActionErrors();
                  try {
                    await deleteM.mutateAsync(removeTarget.id);
                  } catch {
                    /* actionError */
                  }
                }
                setRemoveTarget(null);
              }}
            >
              Delete template
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}
