import { DashboardShell } from "@/components/dashboard/DashboardShell";
import { ProjectProvider } from "./ProjectProvider";

export default function ProjectLayout({
  children,
  params,
}: Readonly<{
  children: React.ReactNode;
  params: Promise<{ projectId: string }>;
}>) {
  return (
    <ProjectProvider params={params}>
      <DashboardShell>{children}</DashboardShell>
    </ProjectProvider>
  );
}
