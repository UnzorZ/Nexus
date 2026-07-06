"use client";

import { useEffect, useState } from "react";
import { PlusIcon } from "@/components/ui/plus";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { DeleteIcon } from "@/components/ui/delete";
import { SettingsIcon } from "@/components/ui/settings";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
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
import { EmptyState, Panel, StatusBadge } from "@/components/dashboard/shared";
import {
  useDeleteConfigValue,
  useProjectConfig,
  useSetConfigValue,
} from "@/features/config/queries";
import type { ConfigValue, ConfigValueType } from "@/features/config/api";
import { toFieldErrors, toMessage } from "@/lib/api/errors";
import { useProject } from "@/app/projects/[projectId]/useProject";

const VALUE_TYPES: ConfigValueType[] = ["STRING", "NUMBER", "BOOLEAN", "JSON"];

const TYPE_TONE: Record<ConfigValueType, "violet" | "blue" | "emerald" | "amber"> =
  {
    STRING: "violet",
    NUMBER: "blue",
    BOOLEAN: "emerald",
    JSON: "amber",
  };

type FormState = { key: string; value: string; valueType: ConfigValueType };

const EMPTY_FORM: FormState = { key: "", value: "", valueType: "STRING" };

/** Mini linter de JSON para el editor del valor de tipo JSON. */
function lintJson(
  value: string,
): { ok: true } | { ok: false; empty: boolean; error: string } {
  if (value.trim() === "") return { ok: false, empty: true, error: "Empty" };
  try {
    JSON.parse(value);
    return { ok: true };
  } catch (exception) {
    return {
      ok: false,
      empty: false,
      error: exception instanceof Error ? exception.message : "Invalid JSON",
    };
  }
}

const CONFIG_MESSAGES = {
  permission: "You don't have permission to manage this project's configuration.",
  forbidden: "Your session expired. Reload the page and try again.",
  notFound: "This config value no longer exists.",
  codes: {},
};

export function ConfigModule() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const projectId = project?.id ?? "";

  const configQ = useProjectConfig(projectId);
  const setM = useSetConfigValue(projectId);
  const deleteM = useDeleteConfigValue(projectId);

  const config = configQ.data ?? null;
  const loading = configQ.isLoading;
  const error = configQ.error ? toMessage(configQ.error) : null;
  const refresh = () => configQ.refetch();

  const canManage = project?.canManage ?? false;
  const [editing, setEditing] = useState<ConfigValue | null | "new">(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [removeTarget, setRemoveTarget] = useState<ConfigValue | null>(null);

  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect */
    if (editing === "new") {
      setForm(EMPTY_FORM);
    } else if (editing) {
      setForm({
        key: editing.key,
        value: editing.value,
        valueType: editing.valueType,
      });
    }
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [editing]);

  const busy = setM.isPending || deleteM.isPending;
  const fieldErrors = toFieldErrors(setM.error);
  const actionError =
    setM.error ?? deleteM.error
      ? toMessage(setM.error ?? deleteM.error, CONFIG_MESSAGES)
      : null;

  const jsonLint = form.valueType === "JSON" ? lintJson(form.value) : null;
  const valueInvalid = jsonLint !== null && !jsonLint.ok;

  const loadingState = projectLoading || (Boolean(project) && loading);

  function resetActionErrors() {
    setM.reset();
    deleteM.reset();
  }

  function openCreate() {
    resetActionErrors();
    setEditing("new");
  }

  function openEdit(value: ConfigValue) {
    resetActionErrors();
    setEditing(value);
  }

  async function onSubmit() {
    const body = {
      key: form.key.trim(),
      value: form.value,
      valueType: form.valueType,
    };
    if (!body.key || editing === null) return;
    resetActionErrors();
    try {
      await setM.mutateAsync(body);
      setEditing(null);
    } catch {
      /* se muestra vía setM.error */
    }
  }

  if (loadingState) {
    return (
      <Panel title="Project configuration">
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
          title="Could not load configuration"
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

  if (!project || !config) {
    return null;
  }

  const formOpen = editing !== null;

  return (
    <Stagger className="grid flex-1 grid-cols-1 gap-6">
      {actionError ? (
        <p className="text-sm text-destructive">{actionError}</p>
      ) : null}

      <Panel
        title="Project configuration"
        description="Define configuration values and feature flags. Boolean values toggle inline."
        action={
          canManage ? (
            <Button size="sm" onClick={openCreate}>
              <PlusIcon size={14} />
              Add config
            </Button>
          ) : undefined
        }
      >
        {config.length === 0 ? (
          <EmptyState
            Icon={SettingsIcon}
            title="No configuration yet"
            description="Add your first config value or feature flag, e.g. feature.beta (boolean) or limits.maxItems (number)."
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Key</TableHead>
                <TableHead>Value</TableHead>
                <TableHead>Type</TableHead>
                {canManage ? <TableHead className="w-8" /> : null}
              </TableRow>
            </TableHeader>
            <TableBody>
              {config.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>
                    <code className="font-mono text-xs text-foreground">
                      {item.key}
                    </code>
                  </TableCell>
                  <TableCell>
                    {item.valueType === "BOOLEAN" ? (
                      <Switch
                        checked={item.value === "true"}
                        disabled={!canManage || busy}
                        aria-label={`Toggle ${item.key}`}
                        onCheckedChange={(checked) =>
                          setM.mutate({
                            key: item.key,
                            value: String(checked),
                            valueType: "BOOLEAN",
                          })
                        }
                      />
                    ) : (
                      <span className="font-mono text-xs text-foreground">
                        {item.value}
                      </span>
                    )}
                  </TableCell>
                  <TableCell>
                    <StatusBadge tone={TYPE_TONE[item.valueType]}>
                      {item.valueType}
                    </StatusBadge>
                  </TableCell>
                  {canManage ? (
                    <TableCell>
                      <DropdownMenu modal={false}>
                        <DropdownMenuTrigger asChild>
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label={`Actions for ${item.key}`}
                          >
                            <EllipsisIcon size={14} />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-40">
                          <DropdownMenuItem
                            disabled={busy}
                            onClick={() => openEdit(item)}
                          >
                            Edit
                          </DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem
                            variant="destructive"
                            disabled={busy}
                            onClick={() => setRemoveTarget(item)}
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
              {editing === "new" ? "Add config" : "Edit config"}
            </DialogTitle>
            <DialogDescription>
              {editing === "new"
                ? "Define a typed configuration value or feature flag."
                : "Update the value and type. The key can't change."}
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cfg-key">Key</Label>
              <Input
                id="cfg-key"
                placeholder="feature.beta"
                value={form.key}
                onChange={(e) => setForm((f) => ({ ...f, key: e.target.value }))}
                disabled={editing !== "new"}
                className="font-mono"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Type</Label>
              <div className="flex flex-wrap gap-1.5">
                {VALUE_TYPES.map((t) => (
                  <Button
                    key={t}
                    type="button"
                    variant={form.valueType === t ? "default" : "outline"}
                    size="sm"
                    onClick={() => setForm((f) => ({ ...f, valueType: t }))}
                  >
                    {t}
                  </Button>
                ))}
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cfg-value">Value</Label>
              {form.valueType === "BOOLEAN" ? (
                <div className="flex items-center gap-2">
                  <Switch
                    id="cfg-value"
                    checked={form.value === "true"}
                    onCheckedChange={(c) =>
                      setForm((f) => ({ ...f, value: String(c) }))
                    }
                  />
                  <span className="text-sm text-muted-foreground">
                    {form.value === "true" ? "true" : "false"}
                  </span>
                </div>
              ) : form.valueType === "JSON" ? (
                <>
                  <Textarea
                    id="cfg-value"
                    rows={8}
                    placeholder={'{\n  "key": "value",\n  "count": 42\n}'}
                    value={form.value}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, value: e.target.value }))
                    }
                    className="font-mono text-xs"
                    spellCheck={false}
                    aria-invalid={valueInvalid && form.value.trim() !== ""}
                  />
                  {jsonLint ? (
                    jsonLint.ok ? (
                      <p className="text-xs text-emerald-600 dark:text-emerald-400">
                        ✓ Valid JSON
                      </p>
                    ) : jsonLint.empty ? (
                      <p className="text-xs text-muted-foreground">
                        Enter valid JSON (object, array, …)
                      </p>
                    ) : (
                      <p className="text-xs text-destructive">{jsonLint.error}</p>
                    )
                  ) : null}
                </>
              ) : (
                <Input
                  id="cfg-value"
                  placeholder={
                    form.valueType === "NUMBER" ? "42" : "any text"
                  }
                  value={form.value}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, value: e.target.value }))
                  }
                  className="font-mono"
                  aria-invalid={Boolean(fieldErrors.value)}
                />
              )}
              {fieldErrors.value ? (
                <p className="text-xs text-destructive">{fieldErrors.value}</p>
              ) : null}
            </div>
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              onClick={onSubmit}
              disabled={
                busy || !form.key.trim() || !form.value || valueInvalid
              }
            >
              {busy ? "Saving…" : editing === "new" ? "Add" : "Save changes"}
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
            <DialogTitle>Delete {removeTarget?.key}?</DialogTitle>
            <DialogDescription>
              The config key{" "}
              <code className="font-mono">{removeTarget?.key}</code> will be
              removed. Apps reading it will get a 404.
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
                    await deleteM.mutateAsync(removeTarget.key);
                  } catch {
                    /* actionError */
                  }
                }
                setRemoveTarget(null);
              }}
            >
              Delete config
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}
