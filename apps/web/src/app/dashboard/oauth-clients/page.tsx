"use client";

import { useState } from "react";
import { motion } from "motion/react";
import { RotateCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { ConnectIcon } from "@/components/ui/connect";
import { EllipsisIcon } from "@/components/ui/ellipsis-icon";
import { KeyCircleIcon } from "@/components/ui/key-circle";
import { LockIcon } from "@/components/ui/lock";
import { LockOpenIcon } from "@/components/ui/lock-open";
import { PlusIcon } from "@/components/ui/plus";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Stagger, fadeUp, tint } from "@/components/dashboard/anim";
import {
  CopyButton,
  MonoChip,
  PageHeader,
  Panel,
  StatTile,
  StatusBadge,
  type Tone,
} from "@/components/dashboard/shared";

type ClientStatus = "active" | "disabled";

type OAuthClient = {
  id: string;
  clientId: string;
  name: string;
  public: boolean;
  redirectUris: string[];
  postLogout: string[];
  grants: string[];
  scopes: string[];
  requirePkce: boolean;
  status: ClientStatus;
  secretMask?: string;
};

const initialClients: OAuthClient[] = [
  {
    id: "c-1",
    clientId: "fshop-web",
    name: "F-Shop Web",
    public: true,
    redirectUris: ["https://fshop.unzor.xyz/auth/callback"],
    postLogout: ["https://fshop.unzor.xyz"],
    grants: ["authorization_code", "refresh_token"],
    scopes: ["openid", "profile", "orders.read"],
    requirePkce: true,
    status: "active",
  },
  {
    id: "c-2",
    clientId: "fshop-backend",
    name: "F-Shop Backend",
    public: false,
    redirectUris: [],
    postLogout: [],
    grants: ["client_credentials"],
    scopes: ["orders.read", "orders.cancel"],
    requirePkce: false,
    status: "active",
    secretMask: "fshop-backend_••••9f2c",
  },
  {
    id: "c-3",
    clientId: "fshop-mobile",
    name: "F-Shop Mobile",
    public: true,
    redirectUris: ["fshop://auth", "https://fshop.unzor.xyz/mobile/callback"],
    postLogout: [],
    grants: ["authorization_code"],
    scopes: ["openid", "profile"],
    requirePkce: true,
    status: "disabled",
  },
];

const grantTone: Record<string, Tone> = {
  authorization_code: "blue",
  refresh_token: "indigo",
  client_credentials: "violet",
};

export default function OAuthClientsPage() {
  const [clients, setClients] = useState<OAuthClient[]>(initialClients);

  const active = clients.filter((c) => c.status === "active").length;
  const pkce = clients.filter((c) => c.requirePkce).length;

  function togglePkce(id: string) {
    setClients((prev) => prev.map((c) => (c.id === id ? { ...c, requirePkce: !c.requirePkce } : c)));
  }
  function setStatus(id: string, status: ClientStatus) {
    setClients((prev) => prev.map((c) => (c.id === id ? { ...c, status } : c)));
  }

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", "F-Shop", "OAuth clients"]}
        title="OAuth clients"
        description="OAuth2/OIDC clients under F-Shop's project issuer. Redirect URIs must match exactly; PKCE is required for public clients."
        badge={<StatusBadge tone="amber" dot pulse>{active} active</StatusBadge>}
        actions={
          <>
            <Button variant="outline">Issuer & JWKS</Button>
            <Button>
              <PlusIcon size={14} />
              New client
            </Button>
          </>
        }
      />

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel title="OAuth clients" description="Client IDs are globally unique for operational simplicity.">
          <div className="mb-4 grid grid-cols-2 divide-x divide-border md:grid-cols-4">
            <StatTile Icon={ConnectIcon} iconBg={tint.amber.bg} iconColor={tint.amber.text} label="Clients" value={clients.length} hint={`${active} active`} />
            <StatTile Icon={ShieldCheckIcon} iconBg={tint.violet.bg} iconColor={tint.violet.text} label="PKCE required" value={pkce} hint="Public clients" />
            <StatTile Icon={LockIcon} iconBg={tint.indigo.bg} iconColor={tint.indigo.text} label="Confidential" value={clients.filter((c) => !c.public).length} hint="Server-side" />
            <StatTile Icon={LockOpenIcon} iconBg={tint.cyan.bg} iconColor={tint.cyan.text} label="Public" value={clients.filter((c) => c.public).length} hint="SPA / mobile" />
          </div>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            {clients.map((client) => (
              <motion.div
                key={client.id}
                variants={fadeUp}
                className="flex flex-col gap-3 rounded-lg ring-1 ring-border bg-card p-4"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex min-w-0 items-center gap-2.5">
                    <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-md ${tint.amber.bg} ${tint.amber.text}`}>
                      <ConnectIcon size={18} />
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-1.5">
                        <span className="truncate text-sm font-semibold">{client.name}</span>
                        {client.public ? (
                          <StatusBadge tone="cyan">Public</StatusBadge>
                        ) : (
                          <StatusBadge tone="indigo">Confidential</StatusBadge>
                        )}
                      </div>
                      <div className="mt-0.5 flex items-center gap-1">
                        <MonoChip>{client.clientId}</MonoChip>
                        <CopyButton value={client.clientId} label="Copy client id" />
                      </div>
                    </div>
                  </div>
                  <DropdownMenu modal={false}>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="icon-sm" aria-label={`Actions for ${client.name}`}>
                        <EllipsisIcon size={14} />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end" className="w-40">
                      <DropdownMenuItem>Edit</DropdownMenuItem>
                      <DropdownMenuItem onClick={() => setStatus(client.id, client.status === "active" ? "disabled" : "active")}>
                        {client.status === "active" ? "Disable" : "Enable"}
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem variant="destructive" className="text-destructive focus:text-destructive">
                        Delete client
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>

                {client.redirectUris.length > 0 ? (
                  <Field label="Redirect URIs">
                    <div className="flex flex-col gap-1">
                      {client.redirectUris.map((uri) => <MonoChip key={uri}>{uri}</MonoChip>)}
                    </div>
                  </Field>
                ) : (
                  <Field label="Redirect URIs">
                    <span className="text-xs text-muted-foreground">None — client credentials only</span>
                  </Field>
                )}

                {client.postLogout.length > 0 ? (
                  <Field label="Post-logout">
                    <div className="flex flex-col gap-1">
                      {client.postLogout.map((uri) => <MonoChip key={uri}>{uri}</MonoChip>)}
                    </div>
                  </Field>
                ) : null}

                <div className="grid gap-3 sm:grid-cols-2">
                  <Field label="Grant types">
                    <div className="flex flex-wrap gap-1">
                      {client.grants.map((g) => (
                        <StatusBadge key={g} tone={grantTone[g] ?? "slate"}>{g}</StatusBadge>
                      ))}
                    </div>
                  </Field>
                  <Field label="Scopes">
                    <div className="flex flex-wrap gap-1">
                      {client.scopes.map((s) => (
                        <StatusBadge key={s} tone="slate">{s}</StatusBadge>
                      ))}
                    </div>
                  </Field>
                </div>

                <div className="flex items-center justify-between border-t pt-3">
                  <label className="flex cursor-pointer items-center gap-2 text-xs">
                    <Switch
                      checked={client.requirePkce}
                      onCheckedChange={() => togglePkce(client.id)}
                      aria-label="Require PKCE"
                    />
                    <span className="flex items-center gap-1">
                      Require PKCE
                      {client.public ? (
                        <StatusBadge tone="violet">recommended</StatusBadge>
                      ) : null}
                    </span>
                  </label>
                  {client.status === "active" ? (
                    <StatusBadge tone="emerald" dot>Active</StatusBadge>
                  ) : (
                    <StatusBadge tone="slate">Disabled</StatusBadge>
                  )}
                </div>

                {!client.public ? (
                  <div className="flex items-center gap-2 rounded-md bg-muted/50 p-2">
                    <KeyCircleIcon size={14} className="shrink-0 text-muted-foreground" />
                    <MonoChip className="flex-1">{client.secretMask}</MonoChip>
                    <CopyButton value={client.secretMask ?? ""} label="Copy secret" />
                    <Button variant="ghost" size="icon-sm" aria-label="Rotate secret">
                      <RotateCw size={13} />
                    </Button>
                  </div>
                ) : (
                  <div className="flex items-center gap-2 text-[11px] text-muted-foreground">
                    <LockOpenIcon size={14} className="shrink-0" />
                    Public client — no secret. PKCE protects token exchange.
                  </div>
                )}
              </motion.div>
            ))}
          </div>
        </Panel>
      </Stagger>
    </Stagger>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </span>
      {children}
    </div>
  );
}
