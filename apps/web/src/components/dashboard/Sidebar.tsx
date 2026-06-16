"use client";

import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import {
  Activity,
  Box,
  Check,
  ChevronDown,
  ClipboardList,
  KeyRound,
  LayoutDashboard,
  Lock,
  Settings,
  Shield,
  ShieldCheck,
  User,
  Users,
} from "lucide-react";

const projectNav = [
  { label: "Overview", href: "/dashboard", icon: LayoutDashboard, active: true },
  { label: "Modules", href: "#", icon: Box },
  { label: "API keys", href: "#", icon: KeyRound },
  { label: "Members", href: "#", icon: Users },
  { label: "Project users", href: "#", icon: User },
  { label: "Permissions", href: "#", icon: ShieldCheck },
  { label: "Roles", href: "#", icon: Shield },
  { label: "OAuth clients", href: "#", icon: Lock },
  { label: "Heartbeat", href: "#", icon: Activity },
  { label: "Audit", href: "#", icon: ClipboardList },
];

const projects = [
  { id: "f-shop", name: "F-Shop", slug: "f-shop", initial: "F", color: "bg-indigo-600" },
  { id: "garagelab", name: "GarageLab", slug: "garagelab", initial: "G", color: "bg-emerald-600" },
  { id: "demo", name: "Demo Project", slug: "demo-project", initial: "D", color: "bg-amber-600" },
  { id: "internal", name: "Internal Tools", slug: "internal-tools", initial: "I", color: "bg-rose-600" },
];

const COLLAPSE_THRESHOLD = 90;

export function Sidebar({ width }: { width: number }) {
  const collapsed = width <= COLLAPSE_THRESHOLD;
  const [isProjectOpen, setIsProjectOpen] = useState(false);
  const [activeProject] = useState(projects[0]);

  return (
    <aside
      className="fixed left-0 top-0 z-30 flex h-screen flex-col border-r border-slate-200 bg-white"
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
          <span className="text-lg font-semibold tracking-tight text-slate-900">
            NEXUS
          </span>
        ) : null}
      </div>

      <div className={`${collapsed ? "px-2 py-3" : "px-4 py-3"}`}>
        {!collapsed ? (
          <p className="px-3 text-[10px] font-semibold uppercase tracking-wider text-slate-400">
            Project
          </p>
        ) : null}
        <div className="relative mt-2">
          <button
            type="button"
            onClick={() => setIsProjectOpen(!isProjectOpen)}
            className={`flex w-full items-center rounded-lg border border-slate-200 bg-white text-left transition hover:border-slate-300 hover:bg-slate-50 ${
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
                  <p className="truncate text-sm font-medium text-slate-900">
                    {activeProject.name}
                  </p>
                  <p className="truncate text-xs text-slate-500">
                    {activeProject.slug}
                  </p>
                </div>
                <ChevronDown
                  className={`h-4 w-4 shrink-0 text-slate-400 transition ${
                    isProjectOpen ? "rotate-180" : ""
                  }`}
                />
              </>
            ) : null}
          </button>

          {!collapsed && isProjectOpen ? (
            <div className="absolute left-0 right-0 top-full z-50 mt-1 rounded-lg border border-slate-200 bg-white py-1 shadow-lg">
              {projects.map((project) => (
                <button
                  key={project.id}
                  type="button"
                  className="flex w-full items-center gap-3 px-3 py-2 text-left transition hover:bg-slate-50"
                >
                  <div
                    className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-xs font-semibold text-white ${project.color}`}
                  >
                    {project.initial}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-slate-900">
                      {project.name}
                    </p>
                    <p className="truncate text-xs text-slate-500">
                      {project.slug}
                    </p>
                  </div>
                  {project.id === activeProject.id ? (
                    <Check className="h-4 w-4 shrink-0 text-indigo-600" />
                  ) : null}
                </button>
              ))}
            </div>
          ) : null}
        </div>
      </div>

      <nav
        className={`flex-1 space-y-0.5 overflow-y-auto py-2 ${
          collapsed ? "px-2" : "px-3"
        }`}
      >
        {projectNav.map((item) => {
          const Icon = item.icon;
          return (
            <Link
              key={item.label}
              href={item.href}
              title={collapsed ? item.label : undefined}
              className={`flex items-center rounded-lg text-sm font-medium transition ${
                item.active
                  ? "bg-indigo-50 text-indigo-700"
                  : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
              } ${
                collapsed
                  ? "h-9 w-9 items-center justify-center p-0"
                  : "gap-3 px-3 py-2"
              }`}
            >
              <Icon className="h-[18px] w-[18px] shrink-0" />
              {!collapsed ? item.label : null}
            </Link>
          );
        })}
      </nav>

      <div className={`border-t border-slate-200 p-3 ${collapsed ? "px-2" : ""}`}>
        <Link
          href="#"
          title={collapsed ? "Project settings" : undefined}
          className={`flex items-center rounded-lg text-sm font-medium text-slate-600 transition hover:bg-slate-100 hover:text-slate-900 ${
            collapsed
              ? "h-9 w-9 items-center justify-center p-0"
              : "gap-3 px-3 py-2"
          }`}
        >
          <Settings className="h-[18px] w-[18px] shrink-0" />
          {!collapsed ? "Project settings" : null}
        </Link>
      </div>
    </aside>
  );
}
