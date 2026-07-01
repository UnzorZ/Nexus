"use client";

import { useEffect, useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { PlusIcon } from "@/components/ui/plus";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { DeleteIcon } from "@/components/ui/delete";
import { KeyCircleIcon } from "@/components/ui/key-circle";
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
import { EmptyState, Panel, StatusBadge } from "@/components/dashboard/shared";
import {
  useCreateVaultSecret,
  useDeleteVaultSecret,
  useProjectVaultSecrets,
  useRevealVaultMasterKey,
  useRevealVaultSecret,
  useRotateVaultMasterKey,
  useRotateVaultSecret,
  useVaultEncryption,
} from "@/features/vault/queries";
import {
  VAULT_CIPHERS,
  type Secret,
  type VaultCipher,
} from "@/features/vault/api";
import { toFieldErrors, toMessage } from "@/lib/api/errors";
import { useProject } from "@/app/projects/[projectId]/useProject";

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

type Editor = { mode: "new" } | { mode: "rotate"; secret: Secret };

const VAULT_MESSAGES = {
  permission: "You don't have permission to manage this project's secrets.",
  forbidden: "Your session expired. Reload the page and try again.",
  notFound: "This secret no longer exists.",
  codes: {},
};

export function VaultModule() {
  const { project, loading: projectLoading, error: projectError } = useProject();
  const projectId = project?.id ?? "";

  const secretsQ = useProjectVaultSecrets(projectId);
  const encryptionQ = useVaultEncryption(projectId);
  const createM = useCreateVaultSecret(projectId);
  const rotateM = useRotateVaultSecret(projectId);
  const deleteM = useDeleteVaultSecret(projectId);
  const revealM = useRevealVaultSecret(projectId);
  const revealMasterM = useRevealVaultMasterKey(projectId);
  const rotateMasterM = useRotateVaultMasterKey(projectId);

  const secrets = secretsQ.data ?? null;
  const encryption = encryptionQ.data ?? null;
  const loading = secretsQ.isLoading;
  const error = secretsQ.error ? toMessage(secretsQ.error) : null;
  const refresh = () => secretsQ.refetch();

  const canManage = project?.canManage ?? false;
  const [editor, setEditor] = useState<Editor | null>(null);
  const [keyInput, setKeyInput] = useState("");
  const [valueInput, setValueInput] = useState("");
  const [cipherInput, setCipherInput] = useState<VaultCipher>("AES_256_GCM");
  const [removeTarget, setRemoveTarget] = useState<Secret | null>(null);

  const [revealed, setRevealed] = useState<Record<string, string>>({});
  const [revealingKey, setRevealingKey] = useState<string | null>(null);

  const [masterKeyShown, setMasterKeyShown] = useState<string | null>(null);
  const [rotateOpen, setRotateOpen] = useState(false);
  const [rotateInput, setRotateInput] = useState("");

  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect */
    if (editor?.mode === "new") {
      setKeyInput("");
      setValueInput("");
      setCipherInput("AES_256_GCM");
    } else if (editor?.mode === "rotate") {
      setKeyInput(editor.secret.key);
      setValueInput("");
      setCipherInput(editor.secret.cipher ?? "AES_256_GCM");
    }
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [editor]);

  const busy =
    createM.isPending || rotateM.isPending || deleteM.isPending || rotateMasterM.isPending;
  const fieldErrors = toFieldErrors(createM.error ?? rotateM.error);
  const actionError =
    createM.error ?? rotateM.error ?? deleteM.error ?? revealM.error ?? rotateMasterM.error
      ? toMessage(
          createM.error ??
            rotateM.error ??
            deleteM.error ??
            revealM.error ??
            rotateMasterM.error,
          VAULT_MESSAGES,
        )
      : null;

  const loadingState = projectLoading || (Boolean(project) && loading);

  function resetActionErrors() {
    createM.reset();
    rotateM.reset();
    deleteM.reset();
    revealM.reset();
    rotateMasterM.reset();
  }

  async function onSubmit() {
    if (!editor) return;
    resetActionErrors();
    try {
      if (editor.mode === "new") {
        if (!keyInput.trim() || !valueInput) return;
        await createM.mutateAsync({
          key: keyInput.trim(),
          value: valueInput,
          cipher: cipherInput,
        });
      } else {
        if (!valueInput) return;
        await rotateM.mutateAsync({
          key: editor.secret.key,
          value: valueInput,
          cipher: cipherInput,
        });
        setRevealed((prev) => {
          const next = { ...prev };
          delete next[editor.secret.key];
          return next;
        });
      }
      setEditor(null);
    } catch {
      /* se muestra vía createM/rotateM.error */
    }
  }

  async function toggleReveal(key: string) {
    if (revealed[key] !== undefined) {
      setRevealed((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
      return;
    }
    resetActionErrors();
    setRevealingKey(key);
    try {
      const result = await revealM.mutateAsync(key);
      setRevealed((prev) => ({ ...prev, [key]: result.value }));
    } catch {
      /* actionError */
    } finally {
      setRevealingKey(null);
    }
  }

  async function toggleMasterKey() {
    if (masterKeyShown !== null) {
      setMasterKeyShown(null);
      return;
    }
    resetActionErrors();
    try {
      const result = await revealMasterM.mutateAsync();
      setMasterKeyShown(result.masterKey);
    } catch {
      /* actionError */
    }
  }

  async function doRotateMasterKey() {
    if (rotateInput.length < 8) return;
    resetActionErrors();
    try {
      await rotateMasterM.mutateAsync(rotateInput);
      setRotateOpen(false);
      setRotateInput("");
      setMasterKeyShown(null);
      setRevealed({});
    } catch {
      /* actionError */
    }
  }

  if (loadingState) {
    return (
      <Panel title="Secrets">
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
          title="Could not load secrets"
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

  if (!project || !secrets) {
    return null;
  }

  const editorOpen = editor !== null;

  return (
    <Stagger className="grid flex-1 grid-cols-1 gap-6">
      {actionError ? (
        <p className="text-sm text-destructive">{actionError}</p>
      ) : null}

      <Panel
        title="Secrets"
        description="Encrypted with the project's master key. Values are masked; reveal with the eye (audited)."
        action={
          canManage ? (
            <Button size="sm" onClick={() => setEditor({ mode: "new" })}>
              <PlusIcon size={14} />
              Add secret
            </Button>
          ) : undefined
        }
      >
        {secrets.length === 0 ? (
          <EmptyState
            Icon={KeyCircleIcon}
            title="No secrets yet"
            description="Add a secret like db.password or stripe.key. Its value is encrypted."
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Key</TableHead>
                <TableHead>Value</TableHead>
                <TableHead>Cipher</TableHead>
                <TableHead>Rotated</TableHead>
                {canManage ? <TableHead className="w-8" /> : null}
              </TableRow>
            </TableHeader>
            <TableBody>
              {secrets.map((secret) => {
                const value = revealed[secret.key];
                const isRevealed = value !== undefined;
                return (
                  <TableRow key={secret.id}>
                    <TableCell>
                      <code className="font-mono text-xs text-foreground">
                        {secret.key}
                      </code>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        {isRevealed ? (
                          <code className="font-mono text-xs text-foreground break-all">
                            {value}
                          </code>
                        ) : (
                          <code className="font-mono text-xs text-muted-foreground">
                            ********
                          </code>
                        )}
                        {canManage ? (
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label={
                              isRevealed ? `Hide ${secret.key}` : `Reveal ${secret.key}`
                            }
                            disabled={revealingKey === secret.key}
                            onClick={() => toggleReveal(secret.key)}
                          >
                            {isRevealed ? <EyeOff size={14} /> : <Eye size={14} />}
                          </Button>
                        ) : null}
                      </div>
                    </TableCell>
                    <TableCell>
                      <StatusBadge tone={secret.cipher === "CHACHA20_POLY1305" ? "blue" : "violet"}>
                        {secret.cipher === "CHACHA20_POLY1305" ? "ChaCha20" : "AES-256"}
                      </StatusBadge>
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
                              onClick={() => setEditor({ mode: "rotate", secret })}
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
                );
              })}
            </TableBody>
          </Table>
        )}
      </Panel>

      {canManage ? (
        <Panel
          title="Encryption"
          description="Master key used to encrypt this project's secrets. Revealing/rotating it is audited."
        >
          <div className="flex flex-col gap-3 text-sm">
            <div className="flex items-center gap-3">
              <span className="w-32 shrink-0 text-xs text-muted-foreground">
                Default cipher
              </span>
              <StatusBadge tone="violet">AES-256-GCM</StatusBadge>
              {encryption ? (
                <span className="text-xs text-muted-foreground">
                  {encryption.secretCount} secret{encryption.secretCount === 1 ? "" : "s"}
                </span>
              ) : null}
            </div>
            <div className="flex items-center gap-3">
              <span className="w-32 shrink-0 text-xs text-muted-foreground">
                Master key
              </span>
              <code className="font-mono text-xs text-foreground break-all">
                {masterKeyShown ?? "••••••••••••••••"}
              </code>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={masterKeyShown ? "Hide master key" : "Reveal master key"}
                disabled={revealMasterM.isPending}
                onClick={toggleMasterKey}
              >
                {masterKeyShown ? <EyeOff size={14} /> : <Eye size={14} />}
              </Button>
              {encryption?.masterKeyOverridden ? (
                <StatusBadge tone="amber">Project key</StatusBadge>
              ) : (
                <StatusBadge tone="slate">Instance key</StatusBadge>
              )}
            </div>
            <div className="flex items-center gap-3">
              <span className="w-32 shrink-0 text-xs text-muted-foreground">
                Change encryption
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={busy}
                onClick={() => {
                  resetActionErrors();
                  setRotateInput("");
                  setRotateOpen(true);
                }}
              >
                Rotate master key
              </Button>
              <span className="text-xs text-muted-foreground">
                Re-encrypts all secrets under a new key.
              </span>
            </div>
          </div>
        </Panel>
      ) : null}

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
                ? "The value is encrypted and masked unless revealed."
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
              <Label>Encryption</Label>
              <div className="flex flex-wrap gap-1.5">
                {VAULT_CIPHERS.map((c) => (
                  <Button
                    key={c}
                    type="button"
                    variant={cipherInput === c ? "default" : "outline"}
                    size="sm"
                    onClick={() => setCipherInput(c)}
                  >
                    {c === "CHACHA20_POLY1305" ? "ChaCha20-Poly1305" : "AES-256-GCM"}
                  </Button>
                ))}
              </div>
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

      {/* Rotate master key dialog */}
      <Dialog
        open={rotateOpen}
        onOpenChange={(open) => {
          if (!open) setRotateOpen(false);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Rotate master key</DialogTitle>
            <DialogDescription>
              Enter a new master key (min 8 chars). Every secret in this project
              is re-encrypted under it. Store it safely — losing it makes secrets
              unrecoverable.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-1.5 py-1">
            <Label htmlFor="vlt-mk">New master key</Label>
            <Textarea
              id="vlt-mk"
              value={rotateInput}
              onChange={(e) => setRotateInput(e.target.value)}
              rows={3}
              className="font-mono text-xs"
              placeholder="a strong, random passphrase"
            />
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button
              variant="destructive"
              onClick={doRotateMasterKey}
              disabled={busy || rotateInput.length < 8}
            >
              {rotateMasterM.isPending ? "Re-encrypting…" : "Rotate & re-encrypt"}
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
