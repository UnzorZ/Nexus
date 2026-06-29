"use client";

import { Users } from "lucide-react";
import { LockIcon } from "@/components/ui/lock";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { UserIcon } from "@/components/ui/user";
import { Button } from "@/components/ui/button";
import {
  MonoChip,
  Panel,
  StatTile,
  StatusBadge,
} from "@/components/dashboard/shared";
import { RelatedLinks } from "@/components/dashboard/modules/ModuleShell";
import { tint } from "@/components/dashboard/anim";

export function IdentityModule() {
  return (
    <>
      <Panel title="Identity" description="Project-isolated users authenticated through this project's OAuth/OIDC realm.">
        <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-4">
          <StatTile Icon={UserIcon} iconBg={tint.indigo.bg} iconColor={tint.indigo.text} label="Project users" value="248" hint="Realm" />
          <StatTile Icon={LockIcon} iconBg={tint.amber.bg} iconColor={tint.amber.text} label="OAuth clients" value="2" hint="Web · Backend" />
          <StatTile Icon={ShieldCheckIcon} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Issuer" value="Active" hint="JWKS served" />
          <StatTile Icon={Users} iconBg={tint.violet.bg} iconColor={tint.violet.text} label="MFA users" value="61" hint="Enrolled" />
        </div>
      </Panel>

      <RelatedLinks
        links={[
          { href: "/dashboard/users", label: "Project users", hint: "248 users in this project realm" },
          { href: "/dashboard/oauth-clients", label: "OAuth clients", hint: "2 clients — demo-web, demo-backend" },
        ]}
      />

      <Panel
        title="Issuer"
        description="Project-scoped OAuth2/OIDC endpoints."
        action={<Button variant="outline" size="sm">View JWKS</Button>}
      >
        <div className="flex flex-col gap-2.5 text-sm">
          <Row label="Issuer" value={<MonoChip>https://nexus.unzor.xyz/p/demo-project</MonoChip>} />
          <Row label="Access token" value="15 minutes" />
          <Row label="Refresh token" value="7 days · rotating" />
          <div className="flex items-center gap-3">
            <span className="w-28 shrink-0 text-xs text-muted-foreground">Multi-issuer</span>
            <StatusBadge tone="amber">Not yet</StatusBadge>
          </div>
        </div>
      </Panel>
    </>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center gap-3">
      <span className="w-28 shrink-0 text-xs text-muted-foreground">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  );
}
