"use client";

import { useEffect, useRef, useState } from "react";
import { Upload } from "lucide-react";
import { PlusIcon } from "@/components/ui/plus";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { DeleteIcon } from "@/components/ui/delete";
import { BellIcon } from "@/components/ui/bell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { HtmlEditor } from "@/components/ui/html-editor";
import {
  KeyValueEditor,
  recordToRows,
  rowsToRecord,
  type KV,
} from "@/components/ui/key-value-editor";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
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
import { EmptyState, Panel, StatusBadge } from "@/components/dashboard/shared";
import {
  useCreateNotifyTemplate,
  useDeleteNotifyTemplate,
  usePreviewNotifyTemplate,
  useProjectNotifications,
  useProjectNotifyTemplates,
  useProjectNotifyVariables,
  useProjectSmtpSettings,
  useSaveNotifyVariables,
  useSaveSmtpSettings,
  useSendTestNotify,
  useUpdateNotifyTemplate,
} from "@/features/notify/queries";
import type {
  Notification,
  NotificationStatus,
  NotificationTemplate,
} from "@/features/notify/api";
import { toFieldErrors, toMessage } from "@/lib/api/errors";
import { useProject } from "@/app/projects/[projectId]/useProject";

const STATUS_TONE: Record<NotificationStatus, "emerald" | "red" | "amber"> = {
  SENT: "emerald",
  FAILED: "red",
  PENDING: "amber",
};

type FormState = { name: string; subject: string; bodyTemplate: string };
const EMPTY_FORM: FormState = { name: "", subject: "", bodyTemplate: "" };

type SmtpForm = {
  host: string;
  port: string;
  username: string;
  from: string;
  password: string;
};
const EMPTY_SMTP: SmtpForm = {
  host: "",
  port: "587",
  username: "",
  from: "",
  password: "",
};

const NOTIFY_MESSAGES = {
  permission: "You don't have permission to manage this project's notifications.",
  forbidden: "Your session expired. Reload the page and try again.",
  notFound: "This template no longer exists.",
  codes: {},
};

export function NotifyModule() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const projectId = project?.id ?? "";

  const templatesQ = useProjectNotifyTemplates(projectId);
  const notificationsQ = useProjectNotifications(projectId);
  const smtpQ = useProjectSmtpSettings(projectId);
  const globalVarsQ = useProjectNotifyVariables(projectId);
  const createM = useCreateNotifyTemplate(projectId);
  const updateM = useUpdateNotifyTemplate(projectId);
  const deleteM = useDeleteNotifyTemplate(projectId);
  const previewM = usePreviewNotifyTemplate(projectId);
  const saveSmtpM = useSaveSmtpSettings(projectId);
  const saveVarsM = useSaveNotifyVariables(projectId);
  const sendTestM = useSendTestNotify(projectId);

  const templates = templatesQ.data ?? null;
  const notifications = notificationsQ.data ?? null;
  const smtp = smtpQ.data ?? null;
  const globalVars = globalVarsQ.data ?? null;
  const loading = templatesQ.isLoading;
  const error = templatesQ.error ? toMessage(templatesQ.error) : null;
  const refresh = () => templatesQ.refetch();

  const canManage = project?.canManage ?? false;

  // Create / edit form
  const [editing, setEditing] = useState<NotificationTemplate | null | "new">(
    null,
  );
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [formVarsRows, setFormVarsRows] = useState<KV[]>([]);
  const [removeTarget, setRemoveTarget] = useState<NotificationTemplate | null>(
    null,
  );

  // Per-template variables dialog (from "...")
  const [varsTarget, setVarsTarget] = useState<NotificationTemplate | null>(null);
  const [varsRows, setVarsRows] = useState<KV[]>([]);

  // Preview
  const [previewTarget, setPreviewTarget] = useState<NotificationTemplate | null>(
    null,
  );
  const [previewVars, setPreviewVars] = useState("{\n  \"name\": \"Ada\"\n}");
  const [previewResult, setPreviewResult] = useState<{
    subject: string;
    body: string;
  } | null>(null);

  // SMTP
  const [smtpForm, setSmtpForm] = useState<SmtpForm>(EMPTY_SMTP);
  const [showSmtpPassword, setShowSmtpPassword] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Global variables
  const [globalRows, setGlobalRows] = useState<KV[]>([]);

  // Test email
  const [testTo, setTestTo] = useState("");
  const [testTemplate, setTestTemplate] = useState<string>("__inline__");
  const [testVarsRows, setTestVarsRows] = useState<KV[]>([]);
  const [testSubject, setTestSubject] = useState("");
  const [testBody, setTestBody] = useState("");
  const [testResult, setTestResult] = useState<Notification | null>(null);

  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect */
    if (editing === "new") {
      setForm(EMPTY_FORM);
      setFormVarsRows([]);
    } else if (editing) {
      setForm({
        name: editing.name,
        subject: editing.subject,
        bodyTemplate: editing.bodyTemplate,
      });
      setFormVarsRows(recordToRows(editing.variables));
    }
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [editing]);

  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect */
    if (globalVars) setGlobalRows(recordToRows(globalVars.variables));
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [globalVars]);

  // Popula el formulario SMTP con la configuración ya guardada. La contraseña
  // nunca se devuelve del backend: se deja en blanco y el placeholder indica
  // "dejar en blanco para mantener la actual". Tras un guardado la query se
  // invalida y este efecto re-sincroniza con los valores persistidos.
  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect */
    if (smtp) {
      setSmtpForm({
        host: smtp.host ?? "",
        port: smtp.port ? String(smtp.port) : "",
        username: smtp.username ?? "",
        from: smtp.from ?? "",
        password: "",
      });
    }
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [smtp]);

  const busy = createM.isPending || updateM.isPending || deleteM.isPending;
  const fieldErrors = toFieldErrors(createM.error ?? updateM.error);
  const actionError =
    createM.error ?? updateM.error ?? deleteM.error ?? previewM.error ?? sendTestM.error
      ? toMessage(
          createM.error ??
            updateM.error ??
            deleteM.error ??
            previewM.error ??
            sendTestM.error,
          NOTIFY_MESSAGES,
        )
      : null;
  const smtpError = saveSmtpM.error
    ? toMessage(saveSmtpM.error, NOTIFY_MESSAGES)
    : null;
  const varsError = saveVarsM.error
    ? toMessage(saveVarsM.error, NOTIFY_MESSAGES)
    : null;

  const loadingState = projectLoading || (Boolean(project) && loading);

  function resetActionErrors() {
    createM.reset();
    updateM.reset();
    deleteM.reset();
    previewM.reset();
    sendTestM.reset();
  }

  function openCreate() {
    resetActionErrors();
    setEditing("new");
  }

  function openEdit(template: NotificationTemplate) {
    resetActionErrors();
    setEditing(template);
  }

  function openVariables(template: NotificationTemplate) {
    resetActionErrors();
    updateM.reset();
    setVarsRows(recordToRows(template.variables));
    setVarsTarget(template);
  }

  function openPreview(template: NotificationTemplate) {
    resetActionErrors();
    setPreviewResult(null);
    setPreviewTarget(template);
  }

  function onUploadHtml(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const text = typeof reader.result === "string" ? reader.result : "";
      setForm((f) => ({ ...f, bodyTemplate: text }));
    };
    reader.readAsText(file);
    event.target.value = "";
  }

  async function onSubmit() {
    const body = {
      name: form.name.trim(),
      subject: form.subject.trim(),
      bodyTemplate: form.bodyTemplate,
      variables: rowsToRecord(formVarsRows),
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

  async function saveVariablesForTarget() {
    if (!varsTarget) return;
    updateM.reset();
    try {
      await updateM.mutateAsync({
        templateId: varsTarget.id,
        name: varsTarget.name,
        subject: varsTarget.subject,
        bodyTemplate: varsTarget.bodyTemplate,
        variables: rowsToRecord(varsRows),
      });
      setVarsTarget(null);
    } catch {
      /* varsError / updateM.error */
    }
  }

  async function doPreview() {
    if (!previewTarget) return;
    let variables: Record<string, string> = {};
    try {
      const parsed = previewVars.trim() ? JSON.parse(previewVars) : {};
      if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
        throw new Error("not an object");
      }
      variables = parsed;
    } catch {
      window.alert("Variables must be a JSON object, e.g. {\"name\":\"Ada\"}");
      return;
    }
    resetActionErrors();
    try {
      const result = await previewM.mutateAsync({
        templateId: previewTarget.id,
        variables,
      });
      setPreviewResult(result);
    } catch {
      setPreviewResult(null);
    }
  }

  async function saveSmtp() {
    saveSmtpM.reset();
    const port = Number(smtpForm.port);
    await saveSmtpM.mutateAsync({
      host: smtpForm.host.trim(),
      port: Number.isFinite(port) && port > 0 ? port : 587,
      username: smtpForm.username.trim(),
      from: smtpForm.from.trim(),
      password: smtpForm.password,
    });
  }

  async function saveGlobalVariables() {
    saveVarsM.reset();
    await saveVarsM.mutateAsync(rowsToRecord(globalRows));
  }

  async function sendTest() {
    if (!testTo.trim()) return;
    resetActionErrors();
    setTestResult(null);
    try {
      const result = await sendTestM.mutateAsync({
        to: testTo.trim(),
        templateName: testTemplate === "__inline__" ? undefined : testTemplate,
        subject: testTemplate === "__inline__" ? testSubject : undefined,
        body: testTemplate === "__inline__" ? testBody : undefined,
        variables: rowsToRecord(testVarsRows),
      });
      setTestResult(result);
    } catch {
      setTestResult(null);
    }
  }

  if (loadingState) {
    return (
      <Panel title="Templates">
        <div className="flex flex-col gap-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      </Panel>
    );
  }

  if (projectError || error) {
    return (
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
    );
  }

  if (!project || !templates) {
    return null;
  }

  const formOpen = editing !== null;
  const smtpConfigured = Boolean(smtp?.host && smtp?.from);
  const smtpUsesOverride = smtp?.host != null;
  const globalCount = Object.keys(globalVars?.variables ?? {}).length;

  return (
    <Stagger className="grid flex-1 grid-cols-1 gap-6">
      {actionError ? (
        <p className="text-sm text-destructive">{actionError}</p>
      ) : null}

      {/* Test email */}
      {canManage ? (
        <Panel
          title="Send test email"
          description="Send a real email to an address you choose. Uses the project SMTP and merges global + per-send variables."
        >
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="ntf-test-to">Recipient</Label>
                <Input
                  id="ntf-test-to"
                  type="email"
                  placeholder="you@example.com"
                  value={testTo}
                  onChange={(e) => setTestTo(e.target.value)}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label>Source</Label>
                <Select value={testTemplate} onValueChange={setTestTemplate}>
                  <SelectTrigger size="sm">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__inline__">Inline (subject + body)</SelectItem>
                    {templates.map((t) => (
                      <SelectItem key={t.id} value={t.name}>
                        {t.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            {testTemplate === "__inline__" ? (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="ntf-test-subject">Subject</Label>
                  <Input
                    id="ntf-test-subject"
                    placeholder="Test subject"
                    value={testSubject}
                    onChange={(e) => setTestSubject(e.target.value)}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="ntf-test-body">Body</Label>
                  <Input
                    id="ntf-test-body"
                    placeholder="Hello {$name}!"
                    value={testBody}
                    onChange={(e) => setTestBody(e.target.value)}
                  />
                </div>
              </div>
            ) : null}
            <div className="flex flex-col gap-1.5">
              <Label>Override variables (per send)</Label>
              <KeyValueEditor
                rows={testVarsRows}
                onChange={setTestVarsRows}
                addLabel="Add override"
              />
            </div>
            <div className="flex items-center gap-3">
              <Button
                size="sm"
                onClick={sendTest}
                disabled={sendTestM.isPending || !testTo.trim()}
              >
                {sendTestM.isPending ? "Sending…" : "Send test email"}
              </Button>
            </div>
          </div>
        </Panel>
      ) : null}

      {/* Global variables */}
      {canManage ? (
        <Panel
          title="Global variables"
          description="Applied to every email in this project. Override per send or per template. Use {$name} in templates."
          action={
            globalCount > 0 ? (
              <StatusBadge tone="blue">{globalCount} globals</StatusBadge>
            ) : undefined
          }
        >
          <div className="flex flex-col gap-3">
            <KeyValueEditor rows={globalRows} onChange={setGlobalRows} />
            {varsError ? (
              <p className="text-xs text-destructive">{varsError}</p>
            ) : null}
            <div className="flex justify-end">
              <Button
                size="sm"
                onClick={saveGlobalVariables}
                disabled={saveVarsM.isPending}
              >
                {saveVarsM.isPending ? "Saving…" : "Save global variables"}
              </Button>
            </div>
          </div>
        </Panel>
      ) : null}

      {/* SMTP */}
      <Panel
        title="Outbound email (SMTP)"
        description="Per-project SMTP relay used to send notifications. Falls back to the instance default when blank."
        action={
          <StatusBadge tone={smtpConfigured ? "emerald" : "slate"} dot>
            {smtpConfigured
              ? smtpUsesOverride
                ? "Project SMTP"
                : "Instance SMTP"
              : "Not configured"}
          </StatusBadge>
        }
      >
        {canManage ? (
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="smtp-host">Host</Label>
                <Input
                  id="smtp-host"
                  placeholder="smtp.example.com"
                  value={smtpForm.host}
                  onChange={(e) =>
                    setSmtpForm((f) => ({ ...f, host: e.target.value }))
                  }
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="smtp-port">Port</Label>
                <Input
                  id="smtp-port"
                  type="number"
                  placeholder="587"
                  value={smtpForm.port}
                  onChange={(e) =>
                    setSmtpForm((f) => ({ ...f, port: e.target.value }))
                  }
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="smtp-user">Username</Label>
                <Input
                  id="smtp-user"
                  placeholder="notifications@example.com"
                  value={smtpForm.username}
                  onChange={(e) =>
                    setSmtpForm((f) => ({ ...f, username: e.target.value }))
                  }
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="smtp-from">From</Label>
                <Input
                  id="smtp-from"
                  placeholder="Project <notifications@example.com>"
                  value={smtpForm.from}
                  onChange={(e) =>
                    setSmtpForm((f) => ({ ...f, from: e.target.value }))
                  }
                />
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="smtp-pass">Password</Label>
              <div className="flex items-center gap-2">
                <Input
                  id="smtp-pass"
                  type={showSmtpPassword ? "text" : "password"}
                  placeholder={
                    smtp?.passwordConfigured
                      ? "•••••••• (leave blank to keep current)"
                      : "smtp password"
                  }
                  value={smtpForm.password}
                  onChange={(e) =>
                    setSmtpForm((f) => ({ ...f, password: e.target.value }))
                  }
                />
                <Button
                  type="button"
                  variant="outline"
                  size="icon-sm"
                  aria-label={showSmtpPassword ? "Hide password" : "Show password"}
                  onClick={() => setShowSmtpPassword((s) => !s)}
                >
                  {showSmtpPassword ? "Hide" : "Show"}
                </Button>
              </div>
            </div>
            {smtpError ? (
              <p className="text-xs text-destructive">{smtpError}</p>
            ) : null}
            <div className="flex justify-end">
              <Button
                size="sm"
                onClick={saveSmtp}
                disabled={
                  saveSmtpM.isPending ||
                  !smtpForm.host.trim() ||
                  !smtpForm.from.trim()
                }
              >
                {saveSmtpM.isPending ? "Saving…" : "Save SMTP settings"}
              </Button>
            </div>
          </div>
        ) : (
          <div className="flex flex-col gap-2 text-sm text-muted-foreground">
            <span>Host: {smtp?.host ?? "—"}</span>
            <span>From: {smtp?.from ?? "—"}</span>
          </div>
        )}
      </Panel>

      {/* Templates */}
      <Panel
        title="Templates"
        description="Use {$variable} placeholders in subject and body (HTML allowed). Variables merge: global → template → per send."
        action={
          canManage ? (
            <Button size="sm" onClick={openCreate}>
              <PlusIcon size={14} />
              New template
            </Button>
          ) : undefined
        }
      >
        {templates.length === 0 ? (
          <EmptyState
            Icon={BellIcon}
            title="No templates yet"
            description="Create a template, e.g. a welcome email with {$name}."
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Subject</TableHead>
                <TableHead>Variables</TableHead>
                {canManage ? <TableHead className="w-8" /> : null}
              </TableRow>
            </TableHeader>
            <TableBody>
              {templates.map((template) => {
                const count = Object.keys(template.variables ?? {}).length;
                return (
                  <TableRow key={template.id}>
                    <TableCell className="font-medium">{template.name}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {template.subject}
                    </TableCell>
                    <TableCell>
                      {count > 0 ? (
                        <StatusBadge tone="blue">{count}</StatusBadge>
                      ) : (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
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
                              disabled={busy || previewM.isPending}
                              onClick={() => openPreview(template)}
                            >
                              Preview
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              disabled={busy}
                              onClick={() => openVariables(template)}
                            >
                              Variables
                            </DropdownMenuItem>
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
                );
              })}
            </TableBody>
          </Table>
        )}
      </Panel>

      {/* Delivery history */}
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

      {/* Create / edit dialog (wider) */}
      <Dialog
        open={formOpen}
        onOpenChange={(open) => {
          if (!open) setEditing(null);
        }}
      >
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>
              {editing === "new" ? "New template" : "Edit template"}
            </DialogTitle>
            <DialogDescription>
              {editing === "new"
                ? "Define an email template with {$variable} placeholders."
                : "Update the template. The name can't change."}
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
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
                  placeholder="Hi {$name}"
                  value={form.subject}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, subject: e.target.value }))
                  }
                />
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between gap-2">
                <Label htmlFor="ntf-body">Body (HTML allowed)</Label>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => fileInputRef.current?.click()}
                >
                  <Upload size={14} />
                  Upload .html
                </Button>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".html,.htm,text/html"
                  className="hidden"
                  onChange={onUploadHtml}
                />
              </div>
              <HtmlEditor
                textareaId="ntf-body"
                value={form.bodyTemplate}
                onChange={(v) => setForm((f) => ({ ...f, bodyTemplate: v }))}
                className="h-72"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Variables (defaults)</Label>
              <KeyValueEditor rows={formVarsRows} onChange={setFormVarsRows} />
              <p className="text-xs text-muted-foreground">
                These fill in when a send doesn&apos;t override them. Globals apply
                underneath.
              </p>
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

      {/* Variables dialog (per template, from "...") */}
      <Dialog
        open={varsTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setVarsTarget(null);
            updateM.reset();
          }
        }}
      >
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Variables — {varsTarget?.name}</DialogTitle>
            <DialogDescription>
              Default values for this template&apos;s placeholders. Globals apply
              underneath; per-send values override these.
            </DialogDescription>
          </DialogHeader>
          <div className="py-1">
            <KeyValueEditor rows={varsRows} onChange={setVarsRows} />
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              onClick={saveVariablesForTarget}
              disabled={updateM.isPending}
            >
              {updateM.isPending ? "Saving…" : "Save variables"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Preview dialog */}
      <Dialog
        open={previewTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setPreviewTarget(null);
            setPreviewResult(null);
            previewM.reset();
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Preview {previewTarget?.name}</DialogTitle>
            <DialogDescription>
              Render the template with sample variables. Triggers like{" "}
              <code className="font-mono">{"{$name}"}</code> are substituted. No
              email is sent.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="ntf-preview-vars">Variables (JSON)</Label>
              <Textarea
                id="ntf-preview-vars"
                value={previewVars}
                onChange={(e) => setPreviewVars(e.target.value)}
                rows={5}
                className="font-mono text-xs"
              />
            </div>
            {previewResult ? (
              <div className="flex flex-col gap-2">
                <div className="flex flex-col gap-1">
                  <span className="text-xs text-muted-foreground">Subject</span>
                  <span className="text-sm font-medium">
                    {previewResult.subject}
                  </span>
                </div>
                <div className="flex flex-col gap-1">
                  <span className="text-xs text-muted-foreground">Body</span>
                  <pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded bg-muted/40 p-2 font-mono text-xs">
                    {previewResult.body}
                  </pre>
                </div>
              </div>
            ) : null}
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Close</Button>
            </DialogClose>
            <Button
              onClick={doPreview}
              disabled={previewM.isPending || !previewTarget}
            >
              {previewM.isPending ? "Rendering…" : "Render preview"}
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

      {/* Test send result */}
      <Dialog
        open={testResult !== null}
        onOpenChange={(open) => {
          if (!open) setTestResult(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {testResult?.status === "SENT"
                ? "Email sent"
                : "Email failed"}
            </DialogTitle>
            <DialogDescription>
              {testResult?.status === "SENT"
                ? "The test email was delivered. Check the inbox."
                : "The SMTP relay rejected or failed to deliver the email."}
            </DialogDescription>
          </DialogHeader>
          {testResult ? (
            <div className="flex flex-col gap-2 py-1 text-sm">
              <div className="flex items-center gap-2">
                <StatusBadge tone={STATUS_TONE[testResult.status]} dot>
                  {testResult.status}
                </StatusBadge>
                <span className="font-mono text-xs text-muted-foreground">
                  {testResult.recipient}
                </span>
              </div>
              <div>
                <span className="text-xs text-muted-foreground">Subject</span>
                <p className="font-medium">{testResult.subject}</p>
              </div>
              {testResult.error ? (
                <div>
                  <span className="text-xs text-destructive">Error</span>
                  <pre className="mt-1 max-h-40 overflow-auto whitespace-pre-wrap rounded bg-muted/40 p-2 font-mono text-xs">
                    {testResult.error}
                  </pre>
                </div>
              ) : null}
            </div>
          ) : null}
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Close</Button>
            </DialogClose>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}
