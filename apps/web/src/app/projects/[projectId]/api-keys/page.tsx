"use client";

import { useState } from "react";
import { PlusIcon } from "@/components/ui/plus";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { DeleteIcon } from "@/components/ui/delete";
import { KeyCircleIcon } from "@/components/ui/key-circle";
import { XIcon } from "@/components/ui/x";
import { Badge } from "@/components/ui/badge";
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
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { Stagger } from "@/components/dashboard/anim";
import {
  EmptyState,
  PageHeader,
  Panel,
  StatusBadge,
} from "@/components/dashboard/shared";
import {
  KNOWN_SCOPES,
  isValidScope,
  type ApiKeySummary,
} from "@/features/api-keys/api";
import {
  useCreateApiKey,
  useDeleteApiKey,
  useProjectApiKeys,
  useRotateApiKey,
  useUpdateApiKey,
  type ApiKeyCreated,
} from "@/features/api-keys/queries";
import { toFieldErrors, toMessage } from "@/lib/api/errors";
import { useProject } from "../useProject";

/** Mensajes de error específicos de API keys para toMessage. */
const API_KEYS_MESSAGES = {
  permission: "You don't have permission to manage API keys for this project.",
  forbidden: "Your session expired. Reload the page and try again.",
  notFound: "This API key no longer exists.",
};

function ApiKeysLoading() {
  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Stagger className="mt-6">
        <Panel title="API keys">
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

function formatInstant(value: string | null): string {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

export default function ProjectApiKeysPage() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const projectId = project?.id ?? "";

  const keysQ = useProjectApiKeys(projectId);
  const createM = useCreateApiKey(projectId);
  const updateM = useUpdateApiKey(projectId);
  const rotateM = useRotateApiKey(projectId);
  const removeM = useDeleteApiKey(projectId);

  const keys = keysQ.data ?? null;
  const loading = keysQ.isLoading;
  const error = keysQ.error ? toMessage(keysQ.error) : null;
  const refresh = () => keysQ.refetch();

  const fieldErrors = toFieldErrors(createM.error);
  const createError =
    createM.error && Object.keys(fieldErrors).length === 0
      ? toMessage(createM.error, API_KEYS_MESSAGES)
      : null;
  const actionErr =
    updateM.error ??
    rotateM.error ??
    removeM.error ??
    // Un error de creación que no es de validación (p. ej. permisos) se
    // muestra en el banner superior, igual que el resto de acciones.
    (createError ? createM.error : null);
  const actionError = actionErr ? toMessage(actionErr, API_KEYS_MESSAGES) : null;
  const busy =
    createM.isPending ||
    updateM.isPending ||
    rotateM.isPending ||
    removeM.isPending;

  const canManage = project?.canManage ?? false;
  const name = project?.name ?? "...";

  const [createOpen, setCreateOpen] = useState(false);
  const [keyName, setKeyName] = useState("");
  const [scopes, setScopes] = useState<string[]>([]);
  const [scopeInput, setScopeInput] = useState("");
  const [scopeError, setScopeError] = useState<string | null>(null);
  const [expiresAt, setExpiresAt] = useState("");

  const [revealed, setRevealed] = useState<ApiKeyCreated | null>(null);
  const [removeTarget, setRemoveTarget] = useState<ApiKeySummary | null>(null);

  const loadingState = projectLoading || (Boolean(project) && loading);

  function resetCreateForm() {
    setKeyName("");
    setScopes([]);
    setScopeInput("");
    setScopeError(null);
    setExpiresAt("");
  }

  function addScope(value: string) {
    const trimmed = value.trim();
    if (!trimmed) return;
    if (!isValidScope(trimmed)) {
      setScopeError("Use the format module:action (e.g. registry:heartbeat).");
      return;
    }
    if (scopes.includes(trimmed)) {
      setScopeInput("");
      setScopeError(null);
      return;
    }
    setScopes((current) => [...current, trimmed]);
    setScopeInput("");
    setScopeError(null);
  }

  async function onCreate() {
    if (!keyName.trim()) return;
    try {
      const created = await createM.mutateAsync({
        name: keyName.trim(),
        scopes,
        expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
      });
      setRevealed(created);
      setCreateOpen(false);
      resetCreateForm();
    } catch {
      /* el error se muestra vía createM.error (fieldErrors/createError) */
    }
  }

  async function onRotate(key: ApiKeySummary) {
    resetActionErrors();
    try {
      const created = await rotateM.mutateAsync(key.id);
      setRevealed(created);
    } catch {
      /* actionError */
    }
  }

  function resetActionErrors() {
    updateM.reset();
    rotateM.reset();
    removeM.reset();
  }

  async function onDisableToggle(key: ApiKeySummary) {
    resetActionErrors();
    try {
      await updateM.mutateAsync({
        keyId: key.id,
        name: key.name,
        status: key.status === "ACTIVE" ? "DISABLED" : "ACTIVE",
        expiresAt: key.expiresAt,
      });
    } catch {
      /* actionError */
    }
  }

  async function onRemove() {
    if (!removeTarget) return;
    resetActionErrors();
    try {
      await removeM.mutateAsync(removeTarget.id);
    } catch {
      /* actionError */
    }
    setRemoveTarget(null);
  }

  if (loadingState) {
    return <ApiKeysLoading />;
  }

  if (projectError || error) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", name, "API keys"]}
          title="API keys"
          description=""
          projectId={project?.id}
        />
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              title="Could not load API keys"
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

  if (!project || !keys) {
    return null;
  }

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, "API keys"]}
        title="API keys"
        description="Keys that identify this project to Nexus over the project API (X-Nexus-Api-Key). Store the secret securely — it's shown only once."
        projectId={project.id}
        badge={
          <StatusBadge tone="emerald" dot pulse>
            {keys.length} keys
          </StatusBadge>
        }
        actions={
          canManage ? (
            <Button
              onClick={() => {
                resetCreateForm();
                setCreateOpen(true);
              }}
            >
              <PlusIcon size={14} />
              New API key
            </Button>
          ) : undefined
        }
      />

      {actionError ? (
        <p className="mt-4 text-sm text-destructive">{actionError}</p>
      ) : null}

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Project API keys"
          description="Disable or rotate keys to revoke access. Rotation creates a replacement key and disables the old one."
        >
          {keys.length === 0 ? (
            <EmptyState
              Icon={KeyCircleIcon}
              title="No API keys yet"
              description="Create a key to let this project authenticate with Nexus."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Prefix</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Scopes</TableHead>
                  <TableHead>Last used</TableHead>
                  <TableHead>Expires</TableHead>
                  {canManage ? <TableHead className="w-8" /> : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {keys.map((key) => (
                  <TableRow key={key.id}>
                    <TableCell className="font-medium">{key.name}</TableCell>
                    <TableCell>
                      <KeyPrefix prefix={key.prefix} />
                    </TableCell>
                    <TableCell>
                      {key.status === "ACTIVE" ? (
                        <StatusBadge tone="emerald" dot>
                          Active
                        </StatusBadge>
                      ) : (
                        <StatusBadge tone="slate">Disabled</StatusBadge>
                      )}
                    </TableCell>
                    <TableCell>
                      {key.scopes.length === 0 ? (
                        <span className="text-xs text-muted-foreground">—</span>
                      ) : (
                        <div className="flex flex-wrap gap-1">
                          {key.scopes.map((scope) => (
                            <Badge
                              key={scope}
                              variant="secondary"
                              className="font-mono text-[10px]"
                            >
                              {scope}
                            </Badge>
                          ))}
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {formatInstant(key.lastUsedAt)}
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {formatInstant(key.expiresAt)}
                    </TableCell>
                    {canManage ? (
                      <TableCell>
                        <DropdownMenu modal={false}>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              aria-label={`Actions for ${key.name}`}
                            >
                              <EllipsisIcon size={14} />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-44">
                            <DropdownMenuItem
                              disabled={busy}
                              onClick={() => onDisableToggle(key)}
                            >
                              {key.status === "ACTIVE" ? "Disable" : "Enable"}
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              disabled={busy}
                              onClick={() => onRotate(key)}
                            >
                              Rotate
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              variant="destructive"
                              disabled={busy}
                              onClick={() => setRemoveTarget(key)}
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

      {/* Create dialog */}
      <Dialog
        open={createOpen}
        onOpenChange={(open) => {
          setCreateOpen(open);
          if (!open) resetCreateForm();
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>New API key</DialogTitle>
            <DialogDescription>
              The full secret is shown only once after creation. Assign the
              scopes this key needs.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="key-name">Name</Label>
              <Input
                id="key-name"
                placeholder="CI / production"
                value={keyName}
                onChange={(e) => setKeyName(e.target.value)}
                aria-invalid={Boolean(fieldErrors.name)}
              />
              {fieldErrors.name ? (
                <p className="text-xs text-destructive">{fieldErrors.name}</p>
              ) : null}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Scopes</Label>
              <div className="flex gap-2">
                <Input
                  placeholder="module:action"
                  value={scopeInput}
                  onChange={(e) => {
                    setScopeInput(e.target.value);
                    setScopeError(null);
                  }}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault();
                      addScope(scopeInput);
                    }
                  }}
                  className="font-mono"
                />
                <Button
                  variant="outline"
                  type="button"
                  onClick={() => addScope(scopeInput)}
                >
                  Add
                </Button>
              </div>
              {scopeError ? (
                <p className="text-xs text-destructive">{scopeError}</p>
              ) : null}
              <div className="flex flex-wrap gap-1">
                {KNOWN_SCOPES.filter((s) => !scopes.includes(s)).map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => addScope(s)}
                    className="rounded-full border border-dashed border-border px-2 py-0.5 font-mono text-[10px] text-muted-foreground hover:border-primary/40 hover:text-foreground"
                  >
                    + {s}
                  </button>
                ))}
              </div>
              {scopes.length > 0 ? (
                <div className="flex flex-wrap gap-1.5">
                  {scopes.map((scope) => (
                    <Badge
                      key={scope}
                      variant="secondary"
                      className="gap-1 font-mono text-[11px]"
                    >
                      {scope}
                      <button
                        type="button"
                        onClick={() =>
                          setScopes((c) => c.filter((x) => x !== scope))
                        }
                        className="text-muted-foreground hover:text-foreground"
                        aria-label={`Remove ${scope}`}
                      >
                        <XIcon size={12} />
                      </button>
                    </Badge>
                  ))}
                </div>
              ) : null}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="key-expiry">Expires (optional)</Label>
              <Input
                id="key-expiry"
                type="datetime-local"
                value={expiresAt}
                onChange={(e) => setExpiresAt(e.target.value)}
              />
            </div>
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button onClick={onCreate} disabled={busy || !keyName.trim()}>
              {busy ? "Creating…" : "Create key"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Show-once secret reveal */}
      <Dialog
        open={revealed !== null}
        onOpenChange={(open) => {
          if (!open) setRevealed(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>API key created</DialogTitle>
            <DialogDescription>
              Copy the secret now. For security, Nexus won&apos;t show it again.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-3 py-1">
            <div className="flex items-center gap-2 rounded-md border bg-muted/40 p-3">
              <code className="flex-1 break-all font-mono text-xs">
                {revealed?.secret}
              </code>
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  if (revealed?.secret) {
                    navigator.clipboard?.writeText(revealed.secret);
                  }
                }}
              >
                Copy
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">
              Send it as the <code className="font-mono">X-Nexus-Api-Key</code>{" "}
              header to the project API.
            </p>
          </div>
          <DialogFooter>
            <Button onClick={() => setRevealed(null)}>Done</Button>
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
              The key is revoked immediately and can&apos;t be recovered.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              variant="destructive"
              disabled={busy}
              onClick={onRemove}
            >
              Delete key
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}

/**
 * Muestra el prefijo de una API key ({@code nxs_<slug>_<partial>}) con un
 * resaltado suave en violeta sobre el fragmento que identifica la key (lo que
 * cambia entre keys); la parte estructural {@code nxs_<slug>_} va en gris. El
 * tooltip va solo sobre ese fragmento para dejar claro que ésa es la parte que
 * identifica la key en toda la web y el SDK. Se divide por el segundo guión bajo
 * (el slug no lleva {@code _}, pero el fragmento base64url sí puede).
 */
function KeyPrefix({ prefix }: { prefix: string }) {
  const firstUnderscore = prefix.indexOf("_");
  const separator =
    firstUnderscore >= 0 ? prefix.indexOf("_", firstUnderscore + 1) : -1;
  const structural = separator >= 0 ? prefix.slice(0, separator + 1) : "";
  const fragment = separator >= 0 ? prefix.slice(separator + 1) : prefix;
  return (
    <code className="font-mono text-xs">
      <span className="text-muted-foreground">{structural}</span>
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <span className="cursor-help text-violet-600 dark:text-violet-300">
              {fragment}
            </span>
          </TooltipTrigger>
          <TooltipContent>
            This fragment identifies the key across the web app and SDK.
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    </code>
  );
}
