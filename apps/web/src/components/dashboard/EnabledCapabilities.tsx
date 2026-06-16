"use client";

import { useRef } from "react";
import { ArrowBigRightIcon } from "@/components/ui/arrow-big-right";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ActivityIcon } from "@/components/ui/activity";
import { ClipboardCheckIcon } from "@/components/ui/clipboard-check";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { UserIcon } from "@/components/ui/user";
import { MotionCard, animHandlers, tint, type AnimIconHandle } from "./anim";

type Capability = {
  id: string;
  title: string;
  description: string;
  metric: string;
  Icon: React.ElementType;
  iconBg: string;
  iconColor: string;
};

const capabilities: Capability[] = [
  {
    id: "identity",
    title: "Identity",
    description: "Project-isolated users and OAuth/OIDC",
    metric: "248 users",
    Icon: UserIcon,
    iconBg: tint.indigo.bg,
    iconColor: tint.indigo.text,
  },
  {
    id: "permissions",
    title: "Permissions",
    description: "Catalog, roles and assignments",
    metric: "18 declared",
    Icon: ShieldCheckIcon,
    iconBg: tint.violet.bg,
    iconColor: tint.violet.text,
  },
  {
    id: "registry",
    title: "Registry",
    description: "Application instances and heartbeat",
    metric: "1 online",
    Icon: ActivityIcon,
    iconBg: tint.cyan.bg,
    iconColor: tint.cyan.text,
  },
  {
    id: "audit",
    title: "Audit",
    description: "Sensitive project actions",
    metric: "Always on",
    Icon: ClipboardCheckIcon,
    iconBg: tint.amber.bg,
    iconColor: tint.amber.text,
  },
];

function CapabilityTile({ cap }: { cap: Capability }) {
  const iconRef = useRef<AnimIconHandle>(null);
  const openRef = useRef<AnimIconHandle>(null);
  return (
    <div
      {...animHandlers(iconRef)}
      className="rounded-md bg-muted/40 p-3 ring-1 ring-transparent transition-colors hover:ring-border"
    >
      <div className="flex items-start justify-between gap-2">
        <div
          className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-md ${cap.iconBg}`}
        >
          <cap.Icon ref={iconRef} size={18} className={cap.iconColor} />
        </div>
        <div className="flex flex-col items-end gap-0.5">
          <span className="text-sm font-semibold">{cap.title}</span>
          <span className="text-[11px] text-muted-foreground">{cap.metric}</span>
        </div>
      </div>
      <p className="mt-2 text-xs text-muted-foreground">{cap.description}</p>
      <Button
        variant="link"
        size="sm"
        {...animHandlers(openRef)}
        className="mt-1 h-auto gap-1 px-0 text-xs"
      >
        Open
        <ArrowBigRightIcon ref={openRef} size={14} />
      </Button>
    </div>
  );
}

export function EnabledCapabilities() {
  const manageRef = useRef<AnimIconHandle>(null);
  return (
    <MotionCard className="h-full">
      <Card className="h-full">
        <CardHeader>
          <CardTitle>Enabled capabilities</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-3">
            {capabilities.map((cap) => (
              <CapabilityTile key={cap.id} cap={cap} />
            ))}
          </div>
        </CardContent>
        <CardFooter className="border-t">
          <Button
            variant="link"
            size="sm"
            {...animHandlers(manageRef)}
            className="ml-auto h-auto gap-1 px-0"
          >
            Manage modules
            <ArrowBigRightIcon ref={manageRef} size={14} />
          </Button>
          <span className="ml-3 text-xs text-muted-foreground">
            Notify is available to enable.
          </span>
        </CardFooter>
      </Card>
    </MotionCard>
  );
}
