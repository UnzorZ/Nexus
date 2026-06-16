"use client";

import { useRef } from "react";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { BookTextIcon } from "@/components/ui/book-text";
import { FileCogIcon } from "@/components/ui/file-cog";
import { KeyCircleIcon } from "@/components/ui/key-circle";
import { PlusIcon } from "@/components/ui/plus";
import { UsersRoundIcon } from "@/components/ui/users-round";
import { ZapIcon } from "@/components/ui/zap-icon";
import { MotionCard, animHandlers, tint, type AnimIconHandle } from "./anim";

type ActionItem = {
  id: string;
  label: string;
  description: string;
  Icon: React.ElementType;
  iconBg: string;
  iconColor: string;
};

const actions: ActionItem[] = [
  {
    id: "api-key",
    label: "Create API key",
    description: "Generate a new project key",
    Icon: KeyCircleIcon,
    iconBg: tint.indigo.bg,
    iconColor: tint.indigo.text,
  },
  {
    id: "member",
    label: "Add member",
    description: "Invite someone to the project",
    Icon: UsersRoundIcon,
    iconBg: tint.emerald.bg,
    iconColor: tint.emerald.text,
  },
  {
    id: "guide",
    label: "Integration guide",
    description: "Connect your application",
    Icon: BookTextIcon,
    iconBg: tint.violet.bg,
    iconColor: tint.violet.text,
  },
  {
    id: "docs",
    label: "API docs",
    description: "Browse the API reference",
    Icon: FileCogIcon,
    iconBg: tint.amber.bg,
    iconColor: tint.amber.text,
  },
];

function ActionButton({ action }: { action: ActionItem }) {
  const iconRef = useRef<AnimIconHandle>(null);
  return (
    <Button
      variant="ghost"
      {...animHandlers(iconRef)}
      className="h-auto w-full justify-start gap-3 px-2 py-2"
    >
      <div
        className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-md ${action.iconBg}`}
      >
        <action.Icon ref={iconRef} size={16} className={action.iconColor} />
      </div>
      <span className="flex-1 text-left">
        <span className="block text-sm font-medium text-foreground">
          {action.label}
        </span>
        <span className="block text-xs font-normal text-muted-foreground">
          {action.description}
        </span>
      </span>
      <PlusIcon size={16} className="shrink-0 text-muted-foreground/60" />
    </Button>
  );
}

export function QuickActions() {
  return (
    <MotionCard className="h-full">
      <Card className="h-full">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ZapIcon size={16} className="text-muted-foreground" />
            Quick actions
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col gap-1">
            {actions.map((action) => (
              <ActionButton key={action.id} action={action} />
            ))}
          </div>
        </CardContent>
      </Card>
    </MotionCard>
  );
}
