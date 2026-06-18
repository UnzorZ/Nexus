"use client";

import { useRouter } from "next/navigation";
import { BoxIcon } from "@/components/ui/box";
import { ChevronRightIcon } from "@/components/ui/chevron-right";
import { ClipboardCheckIcon } from "@/components/ui/clipboard-check";
import { LockIcon } from "@/components/ui/lock";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { Switch } from "@/components/ui/switch";
import { Button } from "@/components/ui/button";
import { Stagger, tint } from "@/components/dashboard/anim";
import {
  PageHeader,
  Panel,
  StatTile,
  StatusBadge,
} from "@/components/dashboard/shared";
import {
  CORE_CAPABILITIES,
  MODULE_CATALOG,
  categoryTone,
  toggleModuleEnabled,
  useModuleEnabled,
} from "@/components/dashboard/modules/catalog";

export default function ModulesPage() {
  const router = useRouter();
  const enabled = useModuleEnabled();
  const enabledCount = MODULE_CATALOG.filter((m) => enabled[m.key]).length;

  function open(key: string) {
    router.push(`/dashboard/modules/${key}`);
  }

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", "F-Shop", "Modules"]}
        title="Modules"
        description="Enable or disable Nexus capabilities for F-Shop. Endpoints belonging to a disabled module return 403 module_disabled."
        badge={
          <StatusBadge tone="emerald" dot pulse>
            {enabledCount} enabled
          </StatusBadge>
        }
        actions={
          <>
            <Button variant="outline">Documentation</Button>
            <Button>Sync declarations</Button>
          </>
        }
      />

      <Stagger className="mt-6 grid flex-1 grid-cols-1 gap-6">
        <Panel
          title="Module gate"
          description="Every project-level API passes these checks in order."
        >
          <div className="grid grid-cols-2 divide-x divide-border md:grid-cols-4">
            <StatTile Icon={ShieldCheckIcon} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Enabled" value={enabledCount} hint={`of ${MODULE_CATALOG.length} configurable`} />
            <StatTile Icon={BoxIcon} iconBg={tint.blue.bg} iconColor={tint.blue.text} label="Configurable" value={MODULE_CATALOG.length} hint="Per-project modules" />
            <StatTile Icon={ClipboardCheckIcon} iconBg={tint.amber.bg} iconColor={tint.amber.text} label="Always on" value={CORE_CAPABILITIES.length} hint="Core capabilities" />
            <StatTile Icon={LockIcon} iconBg={tint.red.bg} iconColor={tint.red.text} label="Default policy" value="Deny" hint="Fail-closed by design" />
          </div>
        </Panel>

        <Panel
          title="Project modules"
          description="Open a module to configure it, or toggle it to enable or disable its APIs."
        >
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
            {MODULE_CATALOG.map((mod) => {
              const isOn = !!enabled[mod.key];
              return (
                <div
                  key={mod.key}
                  role="button"
                  tabIndex={0}
                  onClick={() => open(mod.key)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      open(mod.key);
                    }
                  }}
                  className={`group flex cursor-pointer flex-col gap-3 rounded-lg p-4 transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/40 ${
                    isOn
                      ? "bg-card ring-1 ring-border hover:ring-primary/30"
                      : "border border-dashed border-border bg-muted/40 hover:border-primary/30"
                  }`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-md ${mod.iconBg} ${isOn ? "" : "grayscale"}`}>
                      <mod.Icon size={18} className={mod.iconColor} />
                    </div>
                    <Switch
                      checked={isOn}
                      onClick={(e) => {
                        e.stopPropagation();
                        e.preventDefault();
                      }}
                      onCheckedChange={() => toggleModuleEnabled(mod.key)}
                      aria-label={`Toggle ${mod.name}`}
                    />
                  </div>

                  <div className="flex flex-col gap-1">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold">{mod.name}</span>
                      <StatusBadge tone={categoryTone[mod.category]}>
                        {mod.category}
                      </StatusBadge>
                    </div>
                    <p className="text-xs leading-relaxed text-muted-foreground">
                      {mod.description}
                    </p>
                  </div>

                  <div className="mt-auto flex items-center justify-between border-t pt-3">
                    {isOn ? (
                      <StatusBadge tone="emerald" dot>Enabled</StatusBadge>
                    ) : (
                      <StatusBadge tone="slate">Disabled</StatusBadge>
                    )}
                    <span className="inline-flex items-center gap-1 text-xs font-medium text-primary transition-transform group-hover:translate-x-0.5">
                      {isOn ? "Configure" : "Open"}
                      <ChevronRightIcon size={14} />
                    </span>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="mt-5 flex flex-col gap-2 border-t pt-4 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
            <p>
              Core capabilities are always enabled and cannot be disabled:
              <span className="ml-1 text-foreground">
                {CORE_CAPABILITIES.join(" · ")}
              </span>
              .
            </p>
            <Button variant="link" size="sm" className="h-auto px-0 text-xs">
              View module reference
            </Button>
          </div>
        </Panel>
      </Stagger>
    </Stagger>
  );
}
