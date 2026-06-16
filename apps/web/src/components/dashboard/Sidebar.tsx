"use client";

import { useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { motion } from "motion/react";
import { Check } from "lucide-react";
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

type IconType = React.ElementType;

const projectNav: { label: string; href: string; Icon: IconType }[] = [
  { label: "Overview", href: "/dashboard", Icon: LayoutGridIcon },
  { label: "Modules", href: "#", Icon: BoxIcon },
  { label: "API keys", href: "#", Icon: KeyCircleIcon },
  { label: "Members", href: "#", Icon: UsersRoundIcon },
  { label: "Project users", href: "#", Icon: UserIcon },
  { label: "Permissions", href: "#", Icon: ShieldCheckIcon },
  { label: "Roles", href: "#", Icon: UserCogIcon },
  { label: "OAuth clients", href: "#", Icon: LockIcon },
  { label: "Heartbeat", href: "#", Icon: ActivityIcon },
  { label: "Audit", href: "#", Icon: ClipboardCheckIcon },
];

const projects = [
  { id: "f-shop", name: "F-Shop", slug: "f-shop", initial: "F", color: "bg-indigo-600" },
  { id: "garagelab", name: "GarageLab", slug: "garagelab", initial: "G", color: "bg-emerald-600" },
  { id: "demo", name: "Demo Project", slug: "demo-project", initial: "D", color: "bg-amber-600" },
  { id: "internal", name: "Internal Tools", slug: "internal-tools", initial: "I", color: "bg-rose-600" },
];

const COLLAPSE_THRESHOLD = 90;

function NavItem({
  item,
  active,
  collapsed,
  onSelect,
}: {
  item: { label: string; href: string; Icon: IconType };
  active: boolean;
  collapsed: boolean;
  onSelect: (label: string) => void;
}) {
  const iconRef = useRef<AnimIconHandle>(null);
  return (
    <Link
      href={item.href}
      title={collapsed ? item.label : undefined}
      onClick={() => onSelect(item.label)}
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
  const [activeProject] = useState(projects[0]);
  const [activeLabel, setActiveLabel] = useState("Overview");
  const chevronRef = useRef<AnimIconHandle>(null);
  const footerRef = useRef<AnimIconHandle>(null);

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
        <Image
          src="/nexus-logo-icon.png"
          alt="Nexus"
          width={40}
          height={40}
          className="h-10 w-auto"
          priority
        />
        {!collapsed ? (
          <span className="text-lg font-semibold tracking-tight">NEXUS</span>
        ) : null}
      </div>

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
                className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-sm font-semibold text-white ${activeProject.color}`}
              >
                {activeProject.initial}
              </div>
              {!collapsed ? (
                <>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">
                      {activeProject.name}
                    </p>
                    <p className="truncate text-xs text-muted-foreground">
                      {activeProject.slug}
                    </p>
                  </div>
                  <ChevronDownIcon ref={chevronRef} size={16} className="shrink-0 text-muted-foreground" />
                </>
              ) : null}
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start" className="w-60">
            {projects.map((project) => (
              <DropdownMenuItem key={project.id} className="gap-3">
                <div
                  className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-xs font-semibold text-white ${project.color}`}
                >
                  {project.initial}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{project.name}</p>
                  <p className="truncate text-xs text-muted-foreground">
                    {project.slug}
                  </p>
                </div>
                {project.id === activeProject.id ? (
                  <Check className="size-4 shrink-0 text-primary" />
                ) : null}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <nav
        className={`relative flex-1 space-y-0.5 overflow-y-auto py-2 ${
          collapsed ? "px-2" : "px-3"
        }`}
      >
        {projectNav.map((item) => (
          <NavItem
            key={item.label}
            item={item}
            active={item.label === activeLabel}
            collapsed={collapsed}
            onSelect={setActiveLabel}
          />
        ))}
      </nav>

      <div className={`border-t border-sidebar-border p-3 ${collapsed ? "px-2" : ""}`}>
        <Link
          href="#"
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
    </aside>
  );
}
