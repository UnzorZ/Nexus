"use client";

import { useState } from "react";
import { Trash2 } from "lucide-react";
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
import { ConstructionIcon } from "@/components/ui/construction";
import { KeyCircleIcon } from "@/components/ui/key-circle";
import { TriangleAlertIcon } from "@/components/ui/triangle-alert-icon";
import { Stagger } from "@/components/dashboard/anim";
import {
  MonoChip,
  PageHeader,
  Panel,
  StatusBadge,
} from "@/components/dashboard/shared";

export default function ProjectSettingsPage() {
  const [name, setName] = useState("Unknown project");
  const [description, setDescription] = useState(
    "E-commerce platform and storefront.",
  );
  const [publicBaseUrl, setPublicBaseUrl] = useState("https://app.example.com");
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [confirmText, setConfirmText] = useState("");

  const slug = "demo-project";

  return (
    <Stagger root className="mx-auto flex w-full max-w-4xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", "Unknown project", "Settings"]}
        title="Project settings"
        description="Project boundary metadata. Changes to identity-affecting fields are recorded in the audit log."
        badge={<StatusBadge tone="emerald" dot pulse>Active</StatusBadge>}
        actions={<Button>Save changes</Button>}
      />

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="General"
          description="Display metadata for this project boundary."
        >
          <div className="flex flex-col gap-4">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="proj-name">Name</Label>
                <Input id="proj-name" value={name} onChange={(e) => setName(e.target.value)} />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="proj-slug">Slug</Label>
                <div className="flex items-center gap-1">
                  <Input id="proj-slug" value={slug} readOnly className="font-mono" />
                  <StatusBadge tone="slate">locked</StatusBadge>
                </div>
                <p className="text-[11px] text-muted-foreground">
                  Slugs are immutable after creation.
                </p>
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="proj-desc">Description</Label>
              <Textarea
                id="proj-desc"
                rows={3}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="proj-url">Public base URL</Label>
              <Input
                id="proj-url"
                value={publicBaseUrl}
                onChange={(e) => setPublicBaseUrl(e.target.value)}
                className="font-mono"
              />
              <p className="text-[11px] text-muted-foreground">
                Used for OAuth issuer and redirect URI defaults.
              </p>
            </div>
          </div>
        </Panel>

        <Panel title="Identity" description="Project-scoped OAuth issuer endpoints.">
          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between gap-3">
              <span className="text-xs text-muted-foreground">Project ID</span>
              <MonoChip>prj_demo_8f3a2c</MonoChip>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-xs text-muted-foreground">Issuer</span>
              <MonoChip>https://nexus.unzor.xyz/p/demo-project</MonoChip>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-xs text-muted-foreground">JWKS</span>
              <MonoChip>https://nexus.unzor.xyz/p/demo-project/.well-known/jwks.json</MonoChip>
            </div>
            <div className="flex items-center justify-between gap-3 border-t pt-3">
              <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <KeyCircleIcon size={13} /> Created
              </span>
              <span className="text-xs text-foreground">Jan 12, 2026</span>
            </div>
          </div>
        </Panel>

        <Panel
          title="Danger zone"
          description="Irreversible actions. Require an active Owner."
          cardClassName="ring-destructive/30"
        >
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-start gap-2.5">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-red-500/10 text-red-600 dark:text-red-400">
                <TriangleAlertIcon size={16} />
              </div>
              <div>
                <p className="text-sm font-medium">Delete this project</p>
                <p className="text-xs text-muted-foreground">
                  Permanently removes this project, its keys, users and history. This cannot be undone.
                </p>
              </div>
            </div>
            <Button variant="destructive" onClick={() => setDeleteOpen(true)} className="shrink-0">
              <Trash2 size={14} />
              Delete project
            </Button>
          </div>
        </Panel>

        <div className="flex items-center gap-2 pb-2 text-[11px] text-muted-foreground">
          <ConstructionIcon size={14} className="shrink-0" />
          Prototyping surface — fields are mock and not persisted to the backend.
        </div>
      </Stagger>

      <Dialog open={deleteOpen} onOpenChange={(open) => { setDeleteOpen(open); if (!open) setConfirmText(""); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete this project?</DialogTitle>
            <DialogDescription>
              This permanently deletes the project and everything under it. Type the slug to confirm.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2 py-1">
            <Input
              placeholder={slug}
              value={confirmText}
              onChange={(e) => setConfirmText(e.target.value)}
              className="font-mono"
            />
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Cancel</Button>
            </DialogClose>
            <Button variant="destructive" disabled={confirmText !== slug}>
              Delete permanently
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}
