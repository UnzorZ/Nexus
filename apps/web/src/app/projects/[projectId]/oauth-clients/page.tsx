"use client";

import { useState } from "react";
import { PlusIcon } from "@/components/ui/plus";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { DeleteIcon } from "@/components/ui/delete";
import { LockIcon } from "@/components/ui/lock";
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
import { Stagger } from "@/components/dashboard/anim";
import {
  EmptyState,
  PageHeader,
  Panel,
  StatusBadge,
} from "@/components/dashboard/shared";
import type { OauthClientCreated, OauthClientSummary } from "@/features/oauth-clients/api";
import {
  useCreateOauthClient,
  useDeleteOauthClient,
  useDisableOauthClient,
  useProjectOauthClients,
  useRotateOauthClient,
} from "@/features/oauth-clients/queries";
import { toFieldErrors, toMessage } from "@/lib/api/errors";
import { useProject } from "../useProject";

const OAUTH_CLIENTS_MESSAGES = {
  permission: "You don't have permission to manage OAuth clients for this project.",
  forbidden: "Your session expired. Reload the page and try again.",
  notFound: "This OAuth client no longer exists.",
};

function OauthClientsLoading() {
  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Stagger className="mt-6">
        <Panel title="OAuth clients">
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

export default function ProjectOauthClientsPage() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const projectId = project?.id ?? "";

  const clientsQ = useProjectOauthClients(projectId);
  const createM = useCreateOauthClient(projectId);
  const disableM = useDisableOauthClient(projectId);
  const rotateM = useRotateOauthClient(projectId);
  const removeM = useDeleteOauthClient(projectId);

  const clients = clientsQ.data ?? null;
  const loading = clientsQ.isLoading;
  const error = clientsQ.error ? toMessage(clientsQ.error) : null;
  const refresh = () => clientsQ.refetch();

  const fieldErrors = toFieldErrors(createM.error);
  const createError =
    createM.error && Object.keys(fieldErrors).length === 0
      ? toMessage(createM.error, OAUTH_CLIENTS_MESSAGES)
      : null;
  const actionErr =
    disableM.error ?? rotateM.error ?? removeM.error ?? (createError ? createM.error : null);
  const actionError = actionErr ? toMessage(actionErr, OAUTH_CLIENTS_MESSAGES) : null;
  const busy = createM.isPending || disableM.isPending || rotateM.isPending || removeM.isPending;

  const canManage = project?.canManage ?? false;
  const name = project?.name ?? "...";

  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState({
    name: "",
    redirectUris: "",
    confidential: true,
    requirePkce: true,
    consentRequired: false,
  });
  const [revealed, setRevealed] = useState<OauthClientCreated | null>(null);
  const [removeTarget, setRemoveTarget] = useState<OauthClientSummary | null>(null);

  const loadingState = projectLoading || (Boolean(project) && loading);

  const redirectUris = form.redirectUris
    .split("\n")
    .map((s) => s.trim())
    .filter(Boolean);

  function resetForm() {
    setForm({ name: "", redirectUris: "", confidential: true, requirePkce: true, consentRequired: false });
  }

  async function onCreate() {
    if (!form.name.trim() || redirectUris.length === 0) return;
    // Los clientes públicos (no confidenciales) obligan a PKCE.
    const effectiveRequirePkce = form.confidential ? form.requirePkce : true;
    try {
      const created = await createM.mutateAsync({
        name: form.name.trim(),
        redirectUris,
        requirePkce: effectiveRequirePkce,
        confidential: form.confidential,
        consentRequired: form.consentRequired,
      });
      setCreateOpen(false);
      resetForm();
      if (created.clientSecret) {
        setRevealed(created);
      }
    } catch {
      /* error vía createM.error */
    }
  }

  async function onRotate(client: OauthClientSummary) {
    resetActionErrors();
    try {
      const created = await rotateM.mutateAsync(client.id);
      if (created.clientSecret) setRevealed(created);
    } catch {
      /* actionError */
    }
  }

  function resetActionErrors() {
    disableM.reset();
    rotateM.reset();
    removeM.reset();
  }

  async function onDisable(client: OauthClientSummary) {
    resetActionErrors();
    try {
      await disableM.mutateAsync(client.id);
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
    return <OauthClientsLoading />;
  }

  if (projectError || error) {
    return (
      <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
        <PageHeader
          crumbs={["Projects", name, "OAuth clients"]}
          title="OAuth clients"
          description=""
          projectId={project?.id}
        />
        <Stagger className="mt-6">
          <Panel>
            <EmptyState
              title="Could not load OAuth clients"
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

  if (!project || !clients) {
    return null;
  }

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, "OAuth clients"]}
        title="OAuth clients"
        description={`OAuth/OIDC clients for this project's realm (/p/${project.slug}). Authorization Code + PKCE. Store the secret securely — it's shown only once.`}
        projectId={project.id}
        badge={
          <StatusBadge tone="emerald" dot pulse>
            {clients.length} {clients.length === 1 ? "client" : "clients"}
          </StatusBadge>
        }
        actions={
          canManage ? (
            <Button
              onClick={() => {
                resetForm();
                setCreateOpen(true);
              }}
            >
              <PlusIcon size={14} />
              New client
            </Button>
          ) : undefined
        }
      />

      {actionError ? (
        <p className="mt-4 text-sm text-destructive">{actionError}</p>
      ) : null}

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Project OAuth clients"
          description="Public clients (no secret) must use PKCE. Tokens are issued under this project's issuer with project_id + authz_version claims."
        >
          {clients.length === 0 ? (
            <EmptyState
              Icon={LockIcon}
              title="No OAuth clients yet"
              description="Create a client so apps can sign users in via this project's OAuth/OIDC realm."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Client ID</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Redirect URIs</TableHead>
                  <TableHead>Created</TableHead>
                  {canManage ? <TableHead className="w-8" /> : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {clients.map((client) => (
                  <TableRow key={client.id}>
                    <TableCell className="font-medium">{client.name}</TableCell>
                    <TableCell>
                      <code className="font-mono text-xs text-muted-foreground">
                        {client.clientId}
                      </code>
                    </TableCell>
                    <TableCell>
                      <span className="flex items-center gap-1.5">
                        {client.confidential ? (
                          <StatusBadge tone="blue">Confidential</StatusBadge>
                        ) : (
                          <StatusBadge tone="slate">Public</StatusBadge>
                        )}
                        {client.requirePkce ? (
                          <Badge variant="secondary" className="text-[10px]">
                            PKCE
                          </Badge>
                        ) : null}
                      </span>
                    </TableCell>
                    <TableCell>
                      {client.status === "ACTIVE" ? (
                        <StatusBadge tone="emerald" dot>
                          Active
                        </StatusBadge>
                      ) : (
                        <StatusBadge tone="slate">Disabled</StatusBadge>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-col gap-0.5">
                        {client.redirectUris.slice(0, 2).map((uri) => (
                          <code
                            key={uri}
                            className="max-w-[280px] truncate font-mono text-[10px] text-muted-foreground"
                            title={uri}
                          >
                            {uri}
                          </code>
                        ))}
                        {client.redirectUris.length > 2 ? (
                          <span className="text-[10px] text-muted-foreground">
                            +{client.redirectUris.length - 2} more
                          </span>
                        ) : null}
                      </div>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {formatInstant(client.createdAt)}
                    </TableCell>
                    {canManage ? (
                      <TableCell>
                        <DropdownMenu modal={false}>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              aria-label={`Actions for ${client.name}`}
                            >
                              <EllipsisIcon size={14} />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-44">
                            {client.confidential ? (
                              <DropdownMenuItem
                                disabled={busy}
                                onClick={() => onRotate(client)}
                              >
                                Rotate secret
                              </DropdownMenuItem>
                            ) : null}
                            {client.status === "ACTIVE" ? (
                              <DropdownMenuItem
                                disabled={busy}
                                onClick={() => onDisable(client)}
                              >
                                Disable
                              </DropdownMenuItem>
                            ) : null}
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              variant="destructive"
                              disabled={busy}
                              onClick={() => setRemoveTarget(client)}
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
          if (!open) resetForm();
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>New OAuth client</DialogTitle>
            <DialogDescription>
              {form.confidential
                ? "A confidential client gets a secret (verified with Basic auth). Store it — it's shown only once."
                : "A public client (no secret) must use PKCE."}
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-1">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="oc-name">Name</Label>
              <Input
                id="oc-name"
                placeholder="Web app"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                aria-invalid={Boolean(fieldErrors.name)}
              />
              {fieldErrors.name ? (
                <p className="text-xs text-destructive">{fieldErrors.name}</p>
              ) : null}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="oc-redirects">Redirect URIs (one per line)</Label>
              <textarea
                id="oc-redirects"
                className="min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs"
                placeholder={"https://app.example.com/callback"}
                value={form.redirectUris}
                onChange={(e) => setForm((f) => ({ ...f, redirectUris: e.target.value }))}
              />
              {fieldErrors.redirectUris ? (
                <p className="text-xs text-destructive">{fieldErrors.redirectUris}</p>
              ) : null}
            </div>
            <div className="flex flex-wrap items-center gap-4">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={form.confidential}
                  onChange={(e) => setForm((f) => ({ ...f, confidential: e.target.checked }))}
                  className="size-4"
                />
                Confidential (has secret)
              </label>
              <label className={`flex items-center gap-2 text-sm ${form.confidential ? "" : "opacity-60"}`}>
                <input
                  type="checkbox"
                  checked={form.confidential ? form.requirePkce : true}
                  disabled={!form.confidential}
                  onChange={(e) => setForm((f) => ({ ...f, requirePkce: e.target.checked }))}
                  className="size-4"
                />
                Require PKCE
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={form.consentRequired}
                  onChange={(e) => setForm((f) => ({ ...f, consentRequired: e.target.checked }))}
                  className="size-4"
                />
                Require consent
              </label>
            </div>
            {createError ? (
              <p className="text-xs text-destructive">{createError}</p>
            ) : null}
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button onClick={onCreate} disabled={busy || !form.name.trim() || redirectUris.length === 0}>
              {busy ? "Creating…" : "Create client"}
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
            <DialogTitle>OAuth client created</DialogTitle>
            <DialogDescription>
              Copy the client secret now. For security, Nexus won&apos;t show it again.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-3 py-1">
            <SecretRow label="Client ID" value={revealed?.clientId ?? ""} />
            <SecretRow label="Client secret" value={revealed?.clientSecret ?? ""} />
            <p className="text-xs text-muted-foreground">
              Use these with the OAuth/OIDC endpoints at{" "}
              <code className="font-mono">/p/{project.slug}</code>.
            </p>
          </div>
          <DialogFooter>
            <Button onClick={() => setRevealed(null)}>Done</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete confirmation */}
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
              The OAuth client is revoked immediately. Apps using it will fail to
              authenticate. This can&apos;t be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button variant="destructive" disabled={busy} onClick={onRemove}>
              Delete client
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}

function SecretRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="mb-1 text-[11px] uppercase tracking-wide text-muted-foreground">
        {label}
      </p>
      <div className="flex items-center gap-2 rounded-md border bg-muted/40 p-2">
        <code className="flex-1 break-all font-mono text-xs">{value}</code>
        <Button
          variant="outline"
          size="sm"
          onClick={() => navigator.clipboard?.writeText(value)}
        >
          Copy
        </Button>
      </div>
    </div>
  );
}
