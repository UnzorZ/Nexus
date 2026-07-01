"use client";

import { useEffect, useState } from "react";
import { PlusIcon } from "@/components/ui/plus";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { DeleteIcon } from "@/components/ui/delete";
import { BellIcon } from "@/components/ui/bell";
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
  useCreateNotifyTemplate,
  useDeleteNotifyTemplate,
  useProjectNotifications,
  useProjectNotifyTemplates,
  useUpdateNotifyTemplate,
} from "@/features/notify/queries";
import type {
  NotificationStatus,
  NotificationTemplate,
} from "@/features/notify/api";
import { toFieldErrors, toMessage } from "@/lib/api/errors";
import { useProject } from "../useProject";

const STATUS_TONE: Record<
  NotificationStatus,
  "emerald" | "red" | "amber"
> = {
  SENT: "emerald",
  FAILED: "red",
  PENDING: "amber",
};

function NotifyLoading() {
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

type FormState = { name: string; subject: string; bodyTemplate: string };
const EMPTY_FORM: FormState = { name: "", subject: "", bodyTemplate: "" };

const NOTIFY_MESSAGES = {
  permission: "You don't have permission to manage this project's templates.",
  forbidden: "Your session expired. Reload the page and try again.",
  notFound: "This template no longer exists.",
  codes: {},
};

export default function ProjectNotifyPage() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const projectId = project?.id ?? "";

  const templatesQ = useProjectNotifyTemplates(projectId);
  const notificationsQ = useProjectNotifications(projectId);
  const createM = useCreateNotifyTemplate(projectId);
  const updateM = useUpdateNotifyTemplate(projectId);
  const deleteM = useDeleteNotifyTemplate(projectId);

  const templates = templatesQ.data ?? null;
  const notifications = notificationsQ.data ?? null;
  const loading = templatesQ.isLoading;
  const error = templatesQ.error ? toMessage(templatesQ.error) : null;
  const refresh = () => templatesQ.refetch();

  const canManage = project?.canManage ?? false;
  const name = project?.name ?? "...";
  const [editing, setEditing] = useState<NotificationTemplate | null | "new">(
    null,
  );
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [removeTarget, setRemoveTarget] = useState<NotificationTemplate | null>(
    null,
  );

  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect */
    if (editing === "new") {
      setForm(EMPTY_FORM);
    } else if (editing) {
      setForm({
        name: editing.name,
        subject: editing.subject,
        bodyTemplate: editing.bodyTemplate,
      });
    }
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [editing]);

  const busy = createM.isPending || updateM.isPending || deleteM.isPending;
  const fieldErrors = toFieldErrors(createM.error ?? updateM.error);
  const actionError =
    createM.error ?? updateM.error ?? deleteM.error
      ? toMessage(
          createM.error ?? updateM.error ?? deleteM.error,
          NOTIFY_MESSAGES,
        )
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

  function openEdit(template: NotificationTemplate) {
    resetActionErrors();
    setEditing(template);
  }

  async function onSubmit() {
    const body = {
      name: form.name.trim(),
      subject: form.subject.trim(),
      bodyTemplate: form.bodyTemplate,
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
    return <NotifyLoading />;
  }

  if (projectError || error) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", name, "Notify"]}
          title="Notify"
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
        crumbs={["Projects", name, "Notify"]}
        title="Notify"
        description={`Notification templates for ${name}. Apps send via POST /api/v1/notify/send (scope notify:send), by template or inline.`}
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
          description="Use {{variable}} placeholders in subject and body."
        >
          {templates.length === 0 ? (
            <EmptyState
              Icon={BellIcon}
              title="No templates yet"
              description="Create a template, e.g. a welcome email with {{name}}."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Subject</TableHead>
                  {canManage ? <TableHead className="w-8" /> : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {templates.map((template) => (
                  <TableRow key={template.id}>
                    <TableCell className="font-medium">{template.name}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {template.subject}
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

        <Panel title="Delivery history" description="Last 50 notification attempts.">
          {!notifications || notifications.length === 0 ? (
            <EmptyState
              title="No notifications yet"
              description="Sent notifications appear here once an app calls the send endpoint."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Recipient</TableHead>
                  <TableHead>Subject</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>When</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {notifications.map((n) => (
                  <TableRow key={n.id}>
                    <TableCell className="font-mono text-xs">
                      {n.recipient}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {n.subject}
                    </TableCell>
                    <TableCell>
                      <StatusBadge tone={STATUS_TONE[n.status]} dot>
                        {n.status}
                      </StatusBadge>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {new Date(n.createdAt).toLocaleString()}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
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
                ? "Define an email template with {{variable}} placeholders."
                : "Update the template. The name can't change."}
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="ntf-name">Name</Label>
              <Input
                id="ntf-name"
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
              <Label htmlFor="ntf-subject">Subject</Label>
              <Input
                id="ntf-subject"
                placeholder="Hi {{name}}"
                value={form.subject}
                onChange={(e) =>
                  setForm((f) => ({ ...f, subject: e.target.value }))
                }
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="ntf-body">Body</Label>
              <Textarea
                id="ntf-body"
                placeholder={"Welcome {{name}}!"}
                value={form.bodyTemplate}
                onChange={(e) =>
                  setForm((f) => ({ ...f, bodyTemplate: e.target.value }))
                }
                rows={6}
                className="text-xs"
              />
            </div>
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              onClick={onSubmit}
              disabled={busy || !form.name.trim() || !form.subject.trim()}
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
              The template will be removed. Past deliveries keep their record.
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
