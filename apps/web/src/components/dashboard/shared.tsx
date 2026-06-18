"use client";

import Link from "next/link";
import { Fragment, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Check } from "lucide-react";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ChevronRightIcon } from "@/components/ui/chevron-right";
import { CopyIcon } from "@/components/ui/copy";
import { cn } from "@/lib/utils";
import {
  MotionCard,
  SPRING_SNAPPY,
  animHandlers,
  fadeUp,
  type AnimIconHandle,
} from "./anim";

/* -------------------------------------------------------------------------- */
/*  Tone palette — shared status/semantic colors (dark-aware)                 */
/* -------------------------------------------------------------------------- */

export type Tone =
  | "emerald"
  | "amber"
  | "red"
  | "blue"
  | "violet"
  | "indigo"
  | "cyan"
  | "slate";

const TONES: Record<Tone, string> = {
  emerald: "bg-emerald-500/15 text-emerald-700 dark:text-emerald-300",
  amber: "bg-amber-500/15 text-amber-700 dark:text-amber-300",
  red: "bg-red-500/15 text-red-700 dark:text-red-300",
  blue: "bg-blue-500/15 text-blue-700 dark:text-blue-300",
  violet: "bg-violet-500/15 text-violet-700 dark:text-violet-300",
  indigo: "bg-indigo-500/15 text-indigo-700 dark:text-indigo-300",
  cyan: "bg-cyan-500/15 text-cyan-700 dark:text-cyan-300",
  slate: "bg-muted text-muted-foreground",
};

/* -------------------------------------------------------------------------- */
/*  StatusBadge                                                               */
/* -------------------------------------------------------------------------- */

export function StatusBadge({
  tone = "slate",
  dot = false,
  pulse = false,
  className,
  children,
}: {
  tone?: Tone;
  dot?: boolean;
  pulse?: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <Badge className={cn(TONES[tone], "hover:opacity-100", className)}>
      {dot ? (
        <span
          className={cn(
            "h-1.5 w-1.5 rounded-full bg-current",
            pulse && "nexus-live relative",
          )}
        />
      ) : null}
      {children}
    </Badge>
  );
}

/* -------------------------------------------------------------------------- */
/*  PageHeader — breadcrumb + title row, entrance-synced to the page Stagger  */
/* -------------------------------------------------------------------------- */

// Label → route for breadcrumb links. The last crumb is always the current page
// (no link); any non-last crumb found here becomes a clickable Link.
const CRUMB_HREF: Record<string, string> = {
  Projects: "/dashboard",
  "F-Shop": "/dashboard",
  Modules: "/dashboard/modules",
  "API keys": "/dashboard/api-keys",
  Members: "/dashboard/members",
  "Project users": "/dashboard/users",
  Permissions: "/dashboard/permissions",
  Roles: "/dashboard/roles",
  "OAuth clients": "/dashboard/oauth-clients",
  Heartbeat: "/dashboard/heartbeat",
  Audit: "/dashboard/audit",
  "Project settings": "/dashboard/settings",
};

export function PageHeader({
  crumbs,
  title,
  description,
  badge,
  actions,
}: {
  crumbs: string[];
  title: React.ReactNode;
  description?: React.ReactNode;
  badge?: React.ReactNode;
  actions?: React.ReactNode;
}) {
  return (
    <>
      <motion.nav
        variants={fadeUp}
        className="flex items-center gap-2 text-sm text-muted-foreground"
      >
        {crumbs.map((crumb, index) => {
          const last = index === crumbs.length - 1;
          const href = CRUMB_HREF[crumb];
          return (
            <Fragment key={crumb}>
              {index > 0 ? <ChevronRightIcon size={14} /> : null}
              {last ? (
                <span className="text-foreground">{crumb}</span>
              ) : href ? (
                <Link href={href} className="transition-colors hover:text-foreground">
                  {crumb}
                </Link>
              ) : (
                <span>{crumb}</span>
              )}
            </Fragment>
          );
        })}
      </motion.nav>

      <motion.div
        variants={fadeUp}
        className="mt-4 flex flex-wrap items-start justify-between gap-4"
      >
        <div className="min-w-0">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
            {badge}
          </div>
          {description ? (
            <p className="mt-1 text-sm text-muted-foreground">{description}</p>
          ) : null}
        </div>
        {actions ? (
          <div className="flex items-center gap-3">{actions}</div>
        ) : null}
      </motion.div>
    </>
  );
}

/* -------------------------------------------------------------------------- */
/*  Panel — the standard MotionCard + Card + header block used across pages   */
/* -------------------------------------------------------------------------- */

export function Panel({
  title,
  description,
  action,
  className,
  cardClassName,
  bodyClassName,
  children,
}: {
  title?: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
  cardClassName?: string;
  bodyClassName?: string;
  children: React.ReactNode;
}) {
  return (
    <MotionCard className={cn("h-full", className)}>
      <Card className={cn("h-full", cardClassName)}>
        {title || action ? (
          <CardHeader>
            {title ? <CardTitle>{title}</CardTitle> : null}
            {description ? <CardDescription>{description}</CardDescription> : null}
            {action ? <CardAction>{action}</CardAction> : null}
          </CardHeader>
        ) : null}
        <CardContent className={bodyClassName}>{children}</CardContent>
      </Card>
    </MotionCard>
  );
}

/* -------------------------------------------------------------------------- */
/*  StatTile — compact KPI tile (icon + label + value + hint)                 */
/* -------------------------------------------------------------------------- */

export function StatTile({
  Icon,
  iconBg,
  iconColor,
  label,
  value,
  hint,
}: {
  Icon: React.ElementType;
  iconBg: string;
  iconColor: string;
  label: string;
  value: React.ReactNode;
  hint?: React.ReactNode;
}) {
  const iconRef = useRef<AnimIconHandle>(null);
  return (
    <div
      {...animHandlers(iconRef)}
      className="flex flex-col gap-2 px-3 first:pl-0 last:pr-0"
    >
      <div className="flex items-center gap-2">
        <div
          className={cn(
            "flex h-8 w-8 shrink-0 items-center justify-center rounded-md",
            iconBg,
          )}
        >
          <Icon ref={iconRef} size={16} className={iconColor} />
        </div>
        <p className="text-xs font-medium leading-tight text-muted-foreground">
          {label}
        </p>
      </div>
      <p className="text-2xl font-semibold tracking-tight">{value}</p>
      {hint ? (
        <p className="text-[11px] leading-tight text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  MonoChip — monospace pill for keys / permission keys / client ids         */
/* -------------------------------------------------------------------------- */

export function MonoChip({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex max-w-full items-center rounded-md bg-muted px-1.5 py-0.5 font-mono text-[11px] text-foreground",
        className,
      )}
    >
      <span className="truncate">{children}</span>
    </span>
  );
}

/* -------------------------------------------------------------------------- */
/*  CopyButton — ghost icon button that copies text with a check confirmation  */
/* -------------------------------------------------------------------------- */

export function CopyButton({
  value,
  label = "Copy",
  className,
}: {
  value: string;
  label?: string;
  className?: string;
}) {
  const [copied, setCopied] = useState(false);
  const copyRef = useRef<AnimIconHandle>(null);

  async function copy() {
    try {
      await navigator.clipboard?.writeText(value);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      /* clipboard unavailable — ignore */
    }
  }

  return (
    <button
      type="button"
      {...animHandlers(copyRef)}
      onClick={copy}
      aria-label={copied ? "Copied" : label}
      className={cn(
        "relative flex size-6 shrink-0 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground",
        className,
      )}
    >
      <AnimatePresence mode="wait" initial={false}>
        {copied ? (
          <motion.span
            key="check"
            initial={{ opacity: 0, scale: 0.4 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.4 }}
            transition={SPRING_SNAPPY}
          >
            <Check className="size-3.5 text-emerald-600" />
          </motion.span>
        ) : (
          <motion.span
            key="copy"
            initial={{ opacity: 0, scale: 0.4 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.4 }}
            transition={SPRING_SNAPPY}
          >
            <CopyIcon ref={copyRef} size={14} />
          </motion.span>
        )}
      </AnimatePresence>
    </button>
  );
}

/* -------------------------------------------------------------------------- */
/*  EmptyState — used inside tables / panels when there is nothing to show    */
/* -------------------------------------------------------------------------- */

export function EmptyState({
  Icon,
  title,
  description,
  action,
  className,
}: {
  Icon?: React.ElementType;
  title: string;
  description?: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-2 px-6 py-12 text-center",
        className,
      )}
    >
      {Icon ? (
        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-muted text-muted-foreground">
          <Icon size={18} />
        </div>
      ) : null}
      <p className="text-sm font-medium">{title}</p>
      {description ? (
        <p className="max-w-sm text-xs text-muted-foreground">{description}</p>
      ) : null}
      {action}
    </div>
  );
}
