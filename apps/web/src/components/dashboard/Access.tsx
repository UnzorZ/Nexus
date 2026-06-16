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
import { LockIcon } from "@/components/ui/lock";
import { UserIcon } from "@/components/ui/user";
import { UsersRoundIcon } from "@/components/ui/users-round";
import { MotionCard, animHandlers, tint, type AnimIconHandle } from "./anim";

type AccessItem = {
  id: string;
  title: string;
  value: string;
  subtitle: string;
  Icon: React.ElementType;
  iconBg: string;
  iconColor: string;
};

const accessItems: AccessItem[] = [
  {
    id: "members",
    title: "Nexus members",
    value: "3",
    subtitle: "1 Owner · 2 Members",
    Icon: UsersRoundIcon,
    iconBg: tint.indigo.bg,
    iconColor: tint.indigo.text,
  },
  {
    id: "users",
    title: "Project users",
    value: "248",
    subtitle: "Identity realm",
    Icon: UserIcon,
    iconBg: tint.violet.bg,
    iconColor: tint.violet.text,
  },
  {
    id: "clients",
    title: "OAuth clients",
    value: "2",
    subtitle: "Web app · Backend",
    Icon: LockIcon,
    iconBg: tint.amber.bg,
    iconColor: tint.amber.text,
  },
];

function AccessStat({ item }: { item: AccessItem }) {
  const iconRef = useRef<AnimIconHandle>(null);
  return (
    <div {...animHandlers(iconRef)} className="flex flex-col gap-2 px-3 first:pl-0 last:pr-0">
      <div className="flex items-center gap-2">
        <div
          className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-md ${item.iconBg}`}
        >
          <item.Icon ref={iconRef} size={16} className={item.iconColor} />
        </div>
        <p className="text-xs font-medium leading-tight text-muted-foreground">
          {item.title}
        </p>
      </div>
      <p className="text-2xl font-semibold tracking-tight">{item.value}</p>
      <p className="text-[11px] leading-tight text-muted-foreground">
        {item.subtitle}
      </p>
    </div>
  );
}

export function Access() {
  const manageRef = useRef<AnimIconHandle>(null);
  return (
    <MotionCard className="h-full">
      <Card className="h-full">
        <CardHeader>
          <CardTitle>Access</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-3 gap-4 divide-x divide-border">
            {accessItems.map((item) => (
              <AccessStat key={item.id} item={item} />
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
            Manage access
            <ArrowBigRightIcon ref={manageRef} size={14} />
          </Button>
        </CardFooter>
      </Card>
    </MotionCard>
  );
}
