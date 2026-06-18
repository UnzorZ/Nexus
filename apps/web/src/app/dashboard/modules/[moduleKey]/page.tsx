"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/dashboard/shared";
import { ModuleShell } from "@/components/dashboard/modules/ModuleShell";
import { MODULE_CONFIGS } from "@/components/dashboard/modules/configs";
import { getModule } from "@/components/dashboard/modules/catalog";

export default function ModulePage() {
  const params = useParams<{ moduleKey: string }>();
  const moduleKey = params?.moduleKey;
  const mod = moduleKey ? getModule(moduleKey) : undefined;

  if (!mod) {
    return (
      <div className="mx-auto flex w-full max-w-7xl flex-1 items-center">
        <EmptyState
          title="Module not found"
          description={
            <>
              <code className="font-mono">{String(moduleKey)}</code> isn&apos;t a
              Nexus module.
            </>
          }
          action={
            <Button asChild>
              <Link href="/dashboard/modules">Back to modules</Link>
            </Button>
          }
        />
      </div>
    );
  }

  const Config = MODULE_CONFIGS[mod.key];

  return (
    <ModuleShell module={mod}>
      {Config ? (
        <Config />
      ) : (
        <EmptyState title="Nothing to configure" description="This module has no project-level settings yet." />
      )}
    </ModuleShell>
  );
}
