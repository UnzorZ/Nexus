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
import { KeyCircleIcon } from "@/components/ui/key-circle";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { UserIcon } from "@/components/ui/user";
import { MotionCard, animHandlers, tint, type AnimIconHandle } from "./anim";

type ActivityItem = {
  id: number;
  message: string;
  actor: string;
  time: string;
  Icon: React.ElementType;
  iconColor: string;
  iconBg: string;
};

const activities: ActivityItem[] = [
  {
    id: 1,
    message: "Permissions synchronized",
    actor: "f-shop-api",
    time: "2 minutes ago",
    Icon: ShieldCheckIcon,
    iconColor: tint.indigo.text,
    iconBg: tint.indigo.bg,
  },
  {
    id: 2,
    message: "Heartbeat received",
    actor: "f-shop-api",
    time: "42 seconds ago",
    Icon: ActivityIcon,
    iconColor: tint.emerald.text,
    iconBg: tint.emerald.bg,
  },
  {
    id: 3,
    message: "API key created",
    actor: "Marcos",
    time: "3 days ago",
    Icon: KeyCircleIcon,
    iconColor: tint.amber.text,
    iconBg: tint.amber.bg,
  },
  {
    id: 4,
    message: "Identity module enabled",
    actor: "Marcos",
    time: "5 days ago",
    Icon: UserIcon,
    iconColor: tint.violet.text,
    iconBg: tint.violet.bg,
  },
];

function ActivityRow({ item }: { item: ActivityItem }) {
  const iconRef = useRef<AnimIconHandle>(null);
  return (
    <li
      {...animHandlers(iconRef)}
      className="-mx-2 flex items-center justify-between gap-3 rounded-md px-2 py-1.5 transition-colors hover:bg-muted/60"
    >
      <div className="flex min-w-0 items-center gap-3">
        <div
          className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-md ${item.iconBg}`}
        >
          <item.Icon ref={iconRef} size={16} className={item.iconColor} />
        </div>
        <p className="truncate text-sm font-medium">{item.message}</p>
      </div>
      <div className="flex shrink-0 items-center gap-2 text-xs text-muted-foreground">
        <span>{item.actor}</span>
        <span className="opacity-40">·</span>
        <span>{item.time}</span>
      </div>
    </li>
  );
}

export function RecentActivity() {
  const auditRef = useRef<AnimIconHandle>(null);
  return (
    <MotionCard className="h-full">
      <Card className="h-full">
        <CardHeader>
          <CardTitle>Recent activity</CardTitle>
        </CardHeader>
        <CardContent>
          <ul className="flex flex-col gap-1">
            {activities.map((item) => (
              <ActivityRow key={item.id} item={item} />
            ))}
          </ul>
        </CardContent>
        <CardFooter className="border-t">
          <Button
            variant="link"
            size="sm"
            {...animHandlers(auditRef)}
            className="ml-auto h-auto gap-1 px-0"
          >
            View audit log
            <ArrowBigRightIcon ref={auditRef} size={14} />
          </Button>
        </CardFooter>
      </Card>
    </MotionCard>
  );
}
