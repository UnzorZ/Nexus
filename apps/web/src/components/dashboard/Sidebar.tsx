"use client";

import { useRef } from "react";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion } from "motion/react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ActivityIcon } from "@/components/ui/activity";
import { BoxIcon } from "@/components/ui/box";
import { ChevronDownIcon } from "@/components/ui/chevron-down";
import { ClipboardCheckIcon } from "@/components/ui/clipboard-check";
import { KeyCircleIcon } from "@/components/ui/key-circle";
import { LayoutGridIcon } from "@/components/ui/layout-grid";
import { LockIcon } from "@/components/ui/lock";
import { SettingsIcon } from "@/components/ui/settings";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { UserCogIcon } from "@/components/ui/user-cog-icon";
import { UserIcon } from "@/components/ui/user";
import { UsersRoundIcon } from "@/components/ui/users-round";
import { SPRING, animHandlers, type AnimIconHandle } from "./anim";
import { useProject } from "@/app/projects/[projectId]/useProject";

type IconType = React.ElementType;

type NavItemDef = {
  label: string;
  href: string;
  Icon: IconType;
};

function buildNav(projectId: string): NavItemDef[] {
  return [
    { label: "Overview", href: `/projects/${projectId}`, Icon: LayoutGridIcon },
    { label: "Modules", href: `/projects/${projectId}/modules`, Icon: BoxIcon },
    { label: "API keys", href: `/projects/${projectId}/api-keys`, Icon: KeyCircleIcon },
    { label: "Members", href: `/projects/${projectId}/members`, Icon: UsersRoundIcon },
    { label: "Project users", href: `/projects/${projectId}/users`, Icon: UserIcon },
    { label: "Permissions", href: `/projects/${projectId}/permissions`, Icon: ShieldCheckIcon },
    { label: "Roles", href: `/projects/${projectId}/roles`, Icon: UserCogIcon },
    { label: "OAuth clients", href: `/projects/${projectId}/oauth-clients`, Icon: LockIcon },
    { label: "Heartbeat", href: `/projects/${projectId}/heartbeat`, Icon: ActivityIcon },
    { label: "Audit", href: `/projects/${projectId}/audit`, Icon: ClipboardCheckIcon },
  ];
}

const PROJECT_COLORS = [
  "bg-indigo-600",
  "bg-emerald-600",
  "bg-amber-600",
  "bg-rose-600",
  "bg-cyan-600",
  "bg-violet-600",
  "bg-orange-600",
  "bg-teal-600",
] as const;

function projectColor(name: string) {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return PROJECT_COLORS[Math.abs(hash) % PROJECT_COLORS.length];
}

const COLLAPSE_THRESHOLD = 90;

function NavItem({
  item,
  active,
  collapsed,
}: {
  item: NavItemDef;
  active: boolean;
  collapsed: boolean;
}) {
  const iconRef = useRef<AnimIconHandle>(null);
  return (
    <Link
      href={item.href}
      title={collapsed ? item.label : undefined}
      {...animHandlers(iconRef)}
      className={`relative flex items-center rounded-lg text-sm font-medium transition-colors ${
        active
          ? "bg-primary/10 text-primary"
          : "text-muted-foreground hover:bg-muted hover:text-foreground"
      } ${
        collapsed
          ? "h-9 w-9 items-center justify-center p-0"
          : "gap-3 px-3 py-2"
      }`}
    >
      {active ? (
        <motion.span
          layoutId="sidebar-active"
          aria-hidden
          transition={SPRING}
          className="absolute left-0 top-1/2 h-5 w-1 -translate-y-1/2 rounded-full bg-primary"
        />
      ) : null}
      <item.Icon size={18} ref={iconRef} />
      {!collapsed ? item.label : null}
    </Link>
  );
}

export function Sidebar({ width }: { width: number }) {
  const collapsed = width <= COLLAPSE_THRESHOLD;
  const { project, loading } = useProject();
  const pathname = usePathname();
  const chevronRef = useRef<AnimIconHandle>(null);
  const footerRef = useRef<AnimIconHandle>(null);

  const projectId = project?.id ?? "";
  const navItems = buildNav(projectId);

  // The project index (Overview, href === `/projects/{id}`) must only be active
  // on the exact route, otherwise it matches every nested page too.
  const indexHref = `/projects/${projectId}`;
  function isItemActive(href: string) {
    if (href === indexHref) return pathname === href;
    return pathname === href || pathname.startsWith(`${href}/`);
  }

  const initial = project?.name?.charAt(0).toUpperCase() ?? "?";
  const color = project ? projectColor(project.name) : "bg-muted";

  return (
    <aside
      className="fixed left-0 top-0 z-30 flex h-screen flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground"
      style={{ width: `${width}px` }}
    >
      <div
        className={`flex h-16 items-center ${
          collapsed ? "justify-center px-2" : "gap-3 px-5"
        }`}
      >
        <Link href="/projects">
          <Image
            src="/nexus-logo-icon.png"
            alt="Nexus"
            width={40}
            height={40}
            className="h-10 w-auto"
            priority
          />
        </Link>
        {!collapsed ? (
          <Link
            href="/projects"
            className="text-lg font-semibold tracking-tight hover:text-primary transition-colors"
          >
            NEXUS
          </Link>
        ) : null}
      </div>

      {project && !loading ? (
        <>
          <div className={collapsed ? "px-2 py-3" : "px-4 py-3"}>
            <DropdownMenu modal={false}>
              <DropdownMenuTrigger asChild>
                <button
                  type="button"
                  {...animHandlers(chevronRef)}
                  className={`flex w-full items-center rounded-lg border border-sidebar-border bg-card text-left transition-colors hover:bg-muted ${
                    collapsed ? "justify-center px-2 py-2" : "gap-3 px-3 py-2.5"
                  }`}
                >
                  <div
                    className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-sm font-semibold text-white ${color}`}
                  >
                    {initial}
                  </div>
                  {!collapsed ? (
                    <>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">
                          {project.name}
                        </p>
                        <p className="truncate text-xs text-muted-foreground">
                          {project.slug}
                        </p>
                      </div>
                      <ChevronDownIcon ref={chevronRef} size={16} className="shrink-0 text-muted-foreground" />
                    </>
                  ) : null}
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start" className="w-60">
                <DropdownMenuItem asChild>
                  <Link href="/projects" className="gap-3">
                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-muted text-xs font-semibold text-muted-foreground">
                      <LayoutGridIcon size={14} />
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">
                        All projects
                      </p>
                      <p className="truncate text-xs text-muted-foreground">
                        Switch project
                      </p>
                    </div>
                  </Link>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>

          <nav
            className={`relative flex-1 space-y-0.5 overflow-y-auto py-2 ${
              collapsed ? "px-2" : "px-3"
            }`}
          >
            {navItems.map((item) => (
              <NavItem
                key={item.label}
                item={item}
                active={isItemActive(item.href)}
                collapsed={collapsed}
              />
            ))}
          </nav>

          <div className={`border-t border-sidebar-border p-3 ${collapsed ? "px-2" : ""}`}>
            <Link
              href={`/projects/${projectId}/settings`}
              title={collapsed ? "Project settings" : undefined}
              {...animHandlers(footerRef)}
              className={`flex items-center rounded-lg text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground ${
                collapsed
                  ? "h-9 w-9 items-center justify-center p-0"
                  : "gap-3 px-3 py-2"
              }`}
            >
              <SettingsIcon ref={footerRef} size={18} />
              {!collapsed ? "Project settings" : null}
            </Link>
          </div>
        </>
      ) : collapsed ? (
        <div className="flex-1" />
      ) : (
        <div className="flex flex-1 items-center justify-center px-4">
          <p className="text-xs text-muted-foreground text-center">
            Select a project from the project picker.
          </p>
        </div>
      )}
    </aside>
  );
}
