"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { motion } from "motion/react";
import Image from "next/image";
import Link from "next/link";
import { Database, FolderKanban, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import {
  SPRING_SNAPPY,
  fadeUp,
  staggerContainer,
} from "@/components/dashboard/anim";
import { type ProjectSummary } from "@/features/projects/api";
import { useCreateProject, useProjects } from "@/features/projects/queries";
import { toMessage } from "@/lib/api/errors";

const toneMap = {
  ACTIVE: { bg: "bg-emerald-500/15", text: "text-emerald-700 dark:text-emerald-300", dot: "bg-emerald-500" },
  SUSPENDED: { bg: "bg-amber-500/15", text: "text-amber-700 dark:text-amber-300", dot: "bg-amber-500" },
  ARCHIVED: { bg: "bg-slate-500/15", text: "text-slate-600 dark:text-slate-400", dot: "bg-slate-500" },
} as const;

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

function ProjectCard({
  project,
  index,
}: {
  project: ProjectSummary;
  index: number;
}) {
  const tone = toneMap[project.status];
  const router = useRouter();

  return (
    <motion.div
      variants={fadeUp}
      onClick={() => router.push(`/projects/${project.id}`)}
      className="group cursor-pointer"
    >
      <Card className="h-full transition-[border-color,box-shadow] duration-150 hover:border-primary/30 hover:shadow-sm">
        <CardHeader>
          <div className="flex items-start justify-between gap-4">
            <div className="flex items-center gap-3 min-w-0">
              <div
                className={cn(
                  "flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-sm font-bold text-white",
                  projectColor(project.name),
                )}
              >
                {project.name.charAt(0).toUpperCase()}
              </div>
              <div className="min-w-0">
                <CardTitle className="text-base">{project.name}</CardTitle>
                <CardDescription className="mt-0.5 font-mono text-xs">
                  {project.slug}
                </CardDescription>
              </div>
            </div>
            <Badge
              className={cn(
                "gap-1.5 shrink-0",
                tone.bg,
                tone.text,
              )}
            >
              <span
                className={cn(
                  "h-1.5 w-1.5 rounded-full",
                  tone.dot,
                  project.status === "ACTIVE" ? "nexus-live relative" : "",
                )}
              />
              {project.status.charAt(0) + project.status.slice(1).toLowerCase()}
            </Badge>
          </div>
        </CardHeader>
      </Card>
    </motion.div>
  );
}

export function ProjectsPageShell() {
  const projectsQ = useProjects();
  const createM = useCreateProject();
  const [createOpen, setCreateOpen] = useState(false);
  const router = useRouter();

  const projects = projectsQ.data ?? [];
  const loading = projectsQ.isLoading;
  const error = projectsQ.error ? toMessage(projectsQ.error) : null;
  const loadProjects = () => projectsQ.refetch();

  async function handleCreate(formData: FormData) {
    const slug = formData.get("slug") as string;
    const name = formData.get("name") as string;
    const description = (formData.get("description") as string) || null;
    const publicBaseUrl = (formData.get("publicBaseUrl") as string) || null;
    try {
      const project = await createM.mutateAsync({
        slug,
        name,
        description,
        publicBaseUrl,
      });
      setCreateOpen(false);
      router.push(`/projects/${project.id}`);
    } catch {
      /* la creación falló: el diálogo se queda abierto (sin UI de error aún) */
    }
  }

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-muted/40">
        <div className="flex flex-col items-center gap-4 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600 dark:bg-red-500/10">
            <Database size={24} />
          </div>
          <p className="text-sm text-red-600">{error}</p>
          <Button variant="outline" onClick={loadProjects}>
            Reintentar
          </Button>
        </div>
      </div>
    );
  }

  return (
    <main className="flex min-h-screen flex-col bg-muted/40">
      {/* Top bar */}
      <header className="flex h-16 items-center border-b bg-card px-6">
        <div className="flex items-center gap-3">
          <Image
            src="/nexus-logo-icon.png"
            alt="Nexus"
            width={32}
            height={32}
            className="h-8 w-auto"
            priority
          />
          <span className="text-lg font-semibold tracking-tight">NEXUS</span>
        </div>
        <div className="flex-1" />
        <Dialog open={createOpen} onOpenChange={setCreateOpen}>
          <DialogTrigger asChild>
            <Button className="gap-2">
              <Plus size={16} />
              New project
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Create project</DialogTitle>
              <DialogDescription>
                A project groups modules, API keys, and members under one
                logical service.
              </DialogDescription>
            </DialogHeader>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                handleCreate(new FormData(e.currentTarget));
              }}
              className="flex flex-col gap-4"
            >
              <div className="flex flex-col gap-2">
                <Label htmlFor="name">Name</Label>
                <Input
                  id="name"
                  name="name"
                  placeholder="My project"
                  required
                />
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="slug">Slug</Label>
                <Input
                  id="slug"
                  name="slug"
                  placeholder="my-project"
                  pattern="^[a-z0-9]+(-[a-z0-9]+)*$"
                  title="lowercase letters, numbers and hyphens"
                  required
                />
                <p className="text-xs text-muted-foreground">
                  Used in API paths. Example: <code>my-project</code>
                </p>
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="description">Description (optional)</Label>
                <Textarea
                  id="description"
                  name="description"
                  placeholder="What is this project about?"
                  rows={3}
                />
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="publicBaseUrl">
                  Public base URL (optional)
                </Label>
                <Input
                  id="publicBaseUrl"
                  name="publicBaseUrl"
                  type="url"
                  placeholder="https://my-project.example.com"
                />
              </div>
              <div className="flex justify-end gap-3 pt-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setCreateOpen(false)}
                >
                  Cancel
                </Button>
                <Button type="submit">Create</Button>
              </div>
            </form>
          </DialogContent>
        </Dialog>
      </header>

      {/* Body */}
      <div className="mx-auto flex w-full max-w-5xl flex-1 flex-col px-6 py-10">
        <motion.div variants={fadeUp} className="flex flex-col gap-1">
          <h1 className="text-balance text-2xl font-semibold tracking-tight">
            Projects
          </h1>
          <p className="text-pretty text-sm text-muted-foreground">
            Select a project to manage its modules, API keys, members, and
            settings.
          </p>
        </motion.div>

        {loading ? (
          <div className="mt-10 flex items-center justify-center">
            <motion.p
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="flex items-center gap-2 font-mono text-sm text-slate-500"
            >
              <span className="nexus-live relative h-2 w-2 rounded-full bg-primary text-primary" />
              Loading projects...
            </motion.p>
          </div>
        ) : projects.length === 0 ? (
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={SPRING_SNAPPY}
            className="mt-16 flex flex-col items-center gap-4 text-center"
          >
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-muted">
              <FolderKanban size={28} className="text-muted-foreground" />
            </div>
            <div>
              <p className="text-base font-medium">No projects yet</p>
              <p className="mt-1 text-sm text-muted-foreground">
                Create your first project to get started.
              </p>
            </div>
            <Button
              className="mt-2 gap-2"
              onClick={() => setCreateOpen(true)}
            >
              <Plus size={16} />
              Create project
            </Button>
          </motion.div>
        ) : (
          <motion.div
            variants={staggerContainer}
            initial="hidden"
            animate="show"
            className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
          >
            {projects.map((project, index) => (
              <ProjectCard
                key={project.id}
                project={project}
                index={index}
              />
            ))}
          </motion.div>
        )}
      </div>

      {/* Footer */}
      <footer className="border-t bg-card px-6 py-4 text-center text-xs text-muted-foreground">
        <Link
          href="/dashboard/settings"
          className="transition-colors hover:text-foreground"
        >
          Panel settings
        </Link>
      </footer>
    </main>
  );
}
