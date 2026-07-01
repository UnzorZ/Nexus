"use client";

import { useEffect, useState } from "react";
import { PlusIcon } from "@/components/ui/plus";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { DeleteIcon } from "@/components/ui/delete";
import { KeyCircleIcon } from "@/components/ui/key-circle";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
  useCreateVaultSecret,
  useDeleteVaultSecret,
  useProjectVaultSecrets,
  useRotateVaultSecret,
} from "@/features/vault/queries";
import type { Secret } from "@/features/vault/api";
import { toFieldErrors, toMessage } from "@/lib/api/errors";
import { useProject } from "../useProject";

function VaultLoading() {
  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Stagger className="mt-6">
        <Panel title="Secrets">
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

function formatRelative(iso: string | null): string {
  if (!iso) return "never";
  const sec = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (sec < 60) return `${sec}s ago`;
  const min = Math.round(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.round(min / 60);
  if (hr < 24) return `${hr}h ago`;
  return `${Math.round(hr / 24)}d ago`;
}

type Editor =
  | { mode: "new" }
  | { mode: "rotate"; secret: Secret };

const VAULT_MESSAGES = {
  permission: "You don't have permission to manage this project's secrets.",
  forbidden: "Your session expired. Reload the page and try again.",
  notFound: "This secret no longer exists.",
  codes: {},
};

export default function ProjectVaultPage() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const projectId = project?.id ?? "";

  const secretsQ = useProjectVaultSecrets(projectId);
  const createM = useCreateVaultSecret(projectId);
  const rotateM = useRotateVaultSecret(projectId);
  const deleteM = useDeleteVaultSecret(projectId);

  const secrets = secretsQ.data ?? null;
  const loading = secretsQ.isLoading;
  const error = secretsQ.error ? toMessage(secretsQ.error) : null;
  const refresh = () => secretsQ.refetch();

  const canManage = project?.canManage ?? false;
  const name = project?.name ?? "...";
  const [editor, setEditor] = useState<Editor | null>(null);
  const [keyInput, setKeyInput] = useState("");
  const [valueInput, setValueInput] = useState("");
  const [removeTarget, setRemoveTarget] = useState<Secret | null>(null);

  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect */
    if (editor?.mode === "new") {
      setKeyInput("");
      setValueInput("");
    } else if (editor?.mode === "rotate") {
      setKeyInput(editor.secret.key);
      setValueInput("");
    }
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [editor]);

  const busy = createM.isPending || rotateM.isPending || deleteM.isPending;
  const fieldErrors = toFieldErrors(createM.error ?? rotateM.error);
  const actionError =
    createM.error ?? rotateM.error ?? deleteM.error
      ? toMessage(
          createM.error ?? rotateM.error ?? deleteM.error,
          VAULT_MESSAGES,
        )
      : null;

  const loadingState = projectLoading || (Boolean(project) && loading);

  function resetActionErrors() {
    createM.reset();
    rotateM.reset();
    deleteM.reset();
  }

  async function onSubmit() {
    if (!editor) return;
    resetActionErrors();
    try {
      if (editor.mode === "new") {
        if (!keyInput.trim() || !valueInput) return;
        await createM.mutateAsync({ key: keyInput.trim(), value: valueInput });
      } else {
        if (!valueInput) return;
        await rotateM.mutateAsync({ key: editor.secret.key, value: valueInput });
      }
      setEditor(null);
    } catch {
      /* se muestra vía createM/rotateM.error */
    }
  }

  if (loadingState) {
    return <VaultLoading />;
  }

  if (projectError || error) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", name, "Vault"]}
          title="Vault"
          description=""
          projectId={project?.id}
        />
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              title="Could not load secrets"
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

  if (!project || !secrets) {
    return null;
  }

  const editorOpen = editor !== null;

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, "Vault"]}
        title="Vault"
        description={`Encrypted secrets for ${name}. Apps read them via GET /api/v1/vault/secrets/{key} (scope vault:read). Values are write-only here.`}
        projectId={project.id}
        badge={
          <StatusBadge tone="emerald" dot pulse>
            {secrets.length} secrets
          </StatusBadge>
        }
        actions={
          canManage ? (
            <Button onClick={() => setEditor({ mode: "new" })}>
              <PlusIcon size={14} />
              Add secret
            </Button>
          ) : undefined
        }
      />

      {actionError ? (
        <p className="mt-4 text-sm text-destructive">{actionError}</p>
      ) : null}

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Secrets"
          description="AES-256-GCM encrypted. Values are never readable from the panel."
        >
          {secrets.length === 0 ? (
            <EmptyState
              Icon={KeyCircleIcon}
              title="No secrets yet"
              description="Add a secret like db.password or stripe.key. Its value is encrypted and never shown again."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Key</TableHead>
                  <TableHead>Rotated</TableHead>
                  {canManage ? <TableHead className="w-8" /> : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {secrets.map((secret) => (
                  <TableRow key={secret.id}>
                    <TableCell>
                      <code className="font-mono text-xs text-foreground">
                        {secret.key}
                      </code>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {formatRelative(secret.lastRotatedAt ?? secret.updatedAt)}
                    </TableCell>
                    {canManage ? (
                      <TableCell>
                        <DropdownMenu modal={false}>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              aria-label={`Actions for ${secret.key}`}
                            >
                              <EllipsisIcon size={14} />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-40">
                            <DropdownMenuItem
                              disabled={busy}
                              onClick={() =>
                                setEditor({ mode: "rotate", secret })
                              }
                            >
                              Rotate
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              variant="destructive"
                              disabled={busy}
                              onClick={() => setRemoveTarget(secret)}
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
      </Stagger>

      {/* Create / rotate dialog */}
      <Dialog
        open={editorOpen}
        onOpenChange={(open) => {
          if (!open) setEditor(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editor?.mode === "new" ? "Add secret" : "Rotate secret"}
            </DialogTitle>
            <DialogDescription>
              {editor?.mode === "new"
                ? "The value is encrypted and won't be shown again."
                : `Set a new value for ${editor?.mode === "rotate" ? editor.secret.key : ""}. The old value is overwritten.`}
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="vlt-key">Key</Label>
              <Input
                id="vlt-key"
                placeholder="db.password"
                value={keyInput}
                onChange={(e) => setKeyInput(e.target.value)}
                disabled={editor?.mode !== "new"}
                className="font-mono"
                aria-invalid={Boolean(fieldErrors.key)}
              />
              {fieldErrors.key ? (
                <p className="text-xs text-destructive">{fieldErrors.key}</p>
              ) : null}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="vlt-value">Value</Label>
              <Input
                id="vlt-value"
                type="password"
                placeholder="secret value"
                value={valueInput}
                onChange={(e) => setValueInput(e.target.value)}
                aria-invalid={Boolean(fieldErrors.value)}
              />
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
                busy ||
                (editor?.mode === "new" ? !keyInput.trim() : false) ||
                !valueInput
              }
            >
              {busy
                ? "Saving…"
                : editor?.mode === "new"
                  ? "Add"
                  : "Rotate"}
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
              The secret will be permanently deleted. Apps reading it will get a
              404.
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
              Delete secret
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}
