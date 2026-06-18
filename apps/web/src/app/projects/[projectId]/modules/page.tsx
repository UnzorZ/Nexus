"use client";

import { Button } from "@/components/ui/button";
import { Stagger } from "@/components/dashboard/anim";
import {
  PageHeader,
  Panel,
  EmptyState,
} from "@/components/dashboard/shared";
import { useProject } from "../useProject";

export default function StubPage() {
  const { project } = useProject();
  const name = project?.name ?? "...";

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", name, "Modules"]}
        title="Modules"
        description=""
        projectId={project?.id}
      />

      <Stagger className="mt-6 flex flex-1 flex-col">
        <Panel>
          <EmptyState
            title="Modules"
            description="This section is not yet implemented."
            action={
              <Button variant="outline" disabled>
                Coming soon
              </Button>
            }
          />
        </Panel>
      </Stagger>
    </Stagger>
  );
}
