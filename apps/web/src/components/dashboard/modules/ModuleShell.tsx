"use client";

import { useRef } from "react";
import Link from "next/link";
import { motion } from "motion/react";
import { ArrowBigRightIcon } from "@/components/ui/arrow-big-right";
import { BookTextIcon } from "@/components/ui/book-text";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { TriangleAlertIcon } from "@/components/ui/triangle-alert-icon";
import { cn } from "@/lib/utils";
import { Stagger, animHandlers, fadeUp, type AnimIconHandle } from "../anim";
import { EmptyState, PageHeader, Panel, StatusBadge } from "../shared";
import {
  categoryTone,
  toggleModuleEnabled,
  useModuleEnabled,
  type ModuleMeta,
} from "./catalog";

/**
 * Shared shell for every module page: header with status + enable/disable
 * switch, a clear banner when the module is disabled, and the bespoke body
 * (dimmed and non-interactive while off). Each module renders its own panels
 * as children.
 */
export function ModuleShell({
  module,
  children,
  enabled: enabledProp,
  onToggle,
  projectId,
  projectName,
  canToggle = true,
}: {
  module: ModuleMeta;
  children: React.ReactNode;
  enabled?: boolean;
  onToggle?: () => void;
  projectId?: string;
  projectName?: string;
  canToggle?: boolean;
}) {
  const store = useModuleEnabled();
  const isOn = enabledProp ?? !!store[module.key];
  const handleToggle = onToggle ?? (() => toggleModuleEnabled(module.key));
  const switchDisabled =
    !canToggle || (onToggle !== undefined && enabledProp === undefined);
  const docsRef = useRef<AnimIconHandle>(null);

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={[
          "Projects",
          projectName ?? "Unknown project",
          "Modules",
          module.name,
        ]}
        projectId={projectId}
        title={
          <span className="inline-flex items-center gap-3">
            <span className="flex h-9 w-9 items-center justify-center rounded-md bg-muted">
              <module.Icon size={20} className="text-foreground" />
            </span>
            {module.name}
          </span>
        }
        description={module.description}
        badge={
          <>
            <StatusBadge tone={categoryTone[module.category]}>
              {module.category}
            </StatusBadge>
            {isOn ? (
              <StatusBadge tone="emerald" dot pulse>
                Enabled
              </StatusBadge>
            ) : (
              <StatusBadge tone="slate">Disabled</StatusBadge>
            )}
          </>
        }
        actions={
          <>
            <Button variant="outline" {...animHandlers(docsRef)}>
              <BookTextIcon ref={docsRef} size={14} />
              Documentation
            </Button>
            <div className="flex items-center gap-2 rounded-md border border-input bg-input/20 px-2 py-1">
              <Switch
                size="sm"
                checked={isOn}
                disabled={switchDisabled}
                className="enabled:hover:border-primary"
                onCheckedChange={handleToggle}
                aria-label={`Toggle ${module.name}`}
              />
              <span className="text-xs font-medium text-muted-foreground">
                {isOn ? "Enabled" : "Disabled"}
              </span>
            </div>
          </>
        }
      />

      {!isOn ? (
        <motion.div variants={fadeUp} className="mt-4">
          <DisabledBanner name={module.name} />
        </motion.div>
      ) : null}

      <Stagger
        className={cn(
          "mt-6 grid flex-1 grid-cols-1 gap-6 transition-opacity",
          !isOn && "pointer-events-none opacity-50",
        )}
      >
        {children}
      </Stagger>
    </Stagger>
  );
}

function DisabledBanner({ name }: { name: string }) {
  return (
    <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50/60 p-3 text-xs dark:border-amber-500/30 dark:bg-amber-500/10">
      <TriangleAlertIcon
        size={18}
        className="mt-0.5 shrink-0 text-amber-600 dark:text-amber-400"
      />
      <div>
        <p className="font-semibold text-amber-800 dark:text-amber-200">
          {name} is disabled
        </p>
        <p className="mt-0.5 text-amber-700 dark:text-amber-300/90">
          Its endpoints return{" "}
          <code className="font-mono">403 module_disabled</code>. Turn it on
          above to configure and use this capability.
        </p>
      </div>
    </div>
  );
}

/** A panel of links to other dashboard surfaces (used by hub modules). */
export function RelatedLinks({
  title = "Related surfaces",
  description = "Managed on their dedicated pages.",
  links,
}: {
  title?: string;
  description?: string;
  links: {
    href: string;
    label: string;
    hint?: string;
  }[];
}) {
  return (
    <Panel title={title} description={description}>
      <ul className="flex flex-col gap-1">
        {links.map((link) => (
          <li key={link.href}>
            <Link
              href={link.href}
              className="-mx-2 flex items-center justify-between gap-3 rounded-md px-2 py-2 transition-colors hover:bg-muted"
            >
              <div className="flex min-w-0 flex-col">
                <span className="text-sm font-medium text-foreground">
                  {link.label}
                </span>
                {link.hint ? (
                  <span className="text-xs text-muted-foreground">
                    {link.hint}
                  </span>
                ) : null}
              </div>
              <ArrowBigRightIcon size={14} className="shrink-0 text-muted-foreground" />
            </Link>
          </li>
        ))}
      </ul>
    </Panel>
  );
}

export type ActivityItem = {
  id: string;
  message: string;
  time: string;
  Icon: React.ElementType;
  iconBg: string;
  iconColor: string;
};

export function ActivityPanel({
  title = "Recent activity",
  items,
}: {
  title?: string;
  items: ActivityItem[];
}) {
  return (
    <Panel title={title}>
      {items.length === 0 ? (
        <EmptyState title="No recent activity" />
      ) : (
        <ul className="flex flex-col gap-1">
          {items.map((item) => (
            <li
              key={item.id}
              className="-mx-2 flex items-center justify-between gap-3 rounded-md px-2 py-1.5 transition-colors hover:bg-muted/60"
            >
              <div className="flex min-w-0 items-center gap-2.5">
                <div
                  className={cn(
                    "flex h-7 w-7 shrink-0 items-center justify-center rounded-md",
                    item.iconBg,
                  )}
                >
                  <item.Icon size={14} className={item.iconColor} />
                </div>
                <p className="truncate text-sm font-medium">{item.message}</p>
              </div>
              <span className="shrink-0 text-xs text-muted-foreground">
                {item.time}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}
