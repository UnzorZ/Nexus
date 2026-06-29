"use client";

import { useRef } from "react";
import { motion, useReducedMotion } from "motion/react";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { BoxIcon } from "@/components/ui/box";
import { CircleCheckBigIcon } from "@/components/ui/circle-check-big-icon";
import { KeyCircleIcon } from "@/components/ui/key-circle";
import { ShieldCheckIcon } from "@/components/ui/shield-check";
import { EASE_OUT, MotionCard, animHandlers, type AnimIconHandle } from "./anim";

type Step = {
  id: number;
  label: string;
  status: string;
  Icon: React.ElementType;
};

const steps: Step[] = [
  { id: 1, label: "Project created", status: "Complete", Icon: CircleCheckBigIcon },
  { id: 2, label: "API key issued", status: "2 active", Icon: KeyCircleIcon },
  { id: 3, label: "SDK connected", status: "Last heartbeat 42s ago", Icon: BoxIcon },
  { id: 4, label: "Modules configured", status: "4 enabled", Icon: ShieldCheckIcon },
];

function ReadinessStep({
  step,
  index,
  isLast,
  reduce,
}: {
  step: Step;
  index: number;
  isLast: boolean;
  reduce: boolean | null;
}) {
  const iconRef = useRef<AnimIconHandle>(null);
  const nodeDelay = index * 0.08;

  return (
    <motion.div
      initial={reduce ? false : { opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: EASE_OUT, delay: nodeDelay }}
      className="flex flex-1 items-center"
    >
      <div {...animHandlers(iconRef)} className="flex flex-col items-center">
        <div className="relative flex h-10 w-10 items-center justify-center rounded-full border-2 border-primary bg-primary text-primary-foreground">
          {reduce ? null : (
            <motion.span
              aria-hidden
              className="absolute inset-0 rounded-full bg-primary"
              initial={{ opacity: 0, scale: 1 }}
              animate={{ opacity: [0, 0.35, 0], scale: [1, 1.7, 1.7] }}
              transition={{
                duration: 2.6,
                repeat: Infinity,
                ease: "easeOut",
                delay: 0.4 + index * 0.25,
              }}
            />
          )}
          <step.Icon ref={iconRef} size={20} className="relative" />
        </div>
        <p className="mt-2 text-xs font-medium">
          {step.id}. {step.label}
        </p>
        <p className="mt-0.5 text-[11px] text-emerald-600 dark:text-emerald-400">{step.status}</p>
      </div>

      {isLast ? null : (
        <motion.div
          aria-hidden
          className="mx-2 h-px flex-1 origin-left bg-primary/30"
          initial={reduce ? false : { scaleX: 0 }}
          animate={{ scaleX: 1 }}
          transition={{ duration: 0.45, ease: EASE_OUT, delay: nodeDelay + 0.12 }}
        />
      )}
    </motion.div>
  );
}

export function ProjectReadiness() {
  const reduce = useReducedMotion();

  return (
    <MotionCard className="h-full">
      <Card className="h-full">
        <CardHeader>
          <CardTitle>Project readiness</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between gap-6">
            <div className="flex flex-1 items-center">
              {steps.map((step, index) => (
                <ReadinessStep
                  key={step.id}
                  step={step}
                  index={index}
                  isLast={index === steps.length - 1}
                  reduce={reduce}
                />
              ))}
            </div>

            <motion.div
              initial={reduce ? false : { opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.4, ease: EASE_OUT, delay: steps.length * 0.08 }}
              className="shrink-0"
            >
              <Badge className="gap-1.5 bg-emerald-500/15 text-emerald-700 hover:bg-emerald-500/15 dark:text-emerald-300">
                <CircleCheckBigIcon size={14} />
                Ready
              </Badge>
            </motion.div>
          </div>

          <div className="mt-5 flex items-center gap-2 border-t pt-4 text-xs text-muted-foreground">
            <span className="nexus-live relative h-2 w-2 rounded-full bg-emerald-500 text-emerald-500" />
            This project can use enabled Nexus capabilities through the SDK.
          </div>
        </CardContent>
      </Card>
    </MotionCard>
  );
}
