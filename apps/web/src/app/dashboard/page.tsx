"use client";

import { useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { ShieldAlert } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { BookTextIcon } from "@/components/ui/book-text";
import { InfoIcon } from "@/components/ui/info-icon";
import { PlusIcon } from "@/components/ui/plus";
import { SettingsIcon } from "@/components/ui/settings";
import { TriangleAlertIcon } from "@/components/ui/triangle-alert-icon";
import { XIcon } from "@/components/ui/x";
import { Access } from "@/components/dashboard/Access";
import { EnabledCapabilities } from "@/components/dashboard/EnabledCapabilities";
import { Integration } from "@/components/dashboard/Integration";
import { ProjectReadiness } from "@/components/dashboard/ProjectReadiness";
import { RecentActivity } from "@/components/dashboard/RecentActivity";
import { QuickActions } from "@/components/dashboard/QuickActions";
import { Stagger, SPRING, animHandlers, tint, type AnimIconHandle } from "@/components/dashboard/anim";
import { PageHeader } from "@/components/dashboard/shared";

type Alert = {
  id: string;
  variant: "warning" | "error" | "info";
  title: string;
  message: string;
  action: string;
};

const initialAlerts: Alert[] = [
  {
    id: "oauth",
    variant: "warning",
    title: "Complete OAuth setup",
    message: "Add redirect URIs to your web client.",
    action: "Configure",
  },
];

const alertStyles = {
  warning: {
    border: "border-amber-200 dark:border-amber-500/30",
    bg: "bg-amber-50/50 dark:bg-amber-500/10",
    iconBg: tint.amber.bg,
    iconColor: tint.amber.text,
  },
  error: {
    border: "border-red-200 dark:border-red-500/30",
    bg: "bg-red-50/50 dark:bg-red-500/10",
    iconBg: tint.red.bg,
    iconColor: tint.red.text,
  },
  info: {
    border: "border-blue-200 dark:border-blue-500/30",
    bg: "bg-blue-50/50 dark:bg-blue-500/10",
    iconBg: tint.blue.bg,
    iconColor: tint.blue.text,
  },
};

function AlertBodyIcon({ variant, size }: { variant: Alert["variant"]; size: number }) {
  if (variant === "error") return <ShieldAlert size={size} />;
  if (variant === "info") return <InfoIcon size={size} />;
  return <TriangleAlertIcon size={size} />;
}

function AlertCard({
  alert,
  onDismiss,
}: {
  alert: Alert;
  onDismiss: (id: string) => void;
}) {
  const styles = alertStyles[alert.variant];
  const xRef = useRef<AnimIconHandle>(null);
  return (
    <motion.div
      initial={{ opacity: 0, x: 24, scale: 0.96 }}
      animate={{ opacity: 1, x: 0, scale: 1 }}
      exit={{ opacity: 0, x: 24, scale: 0.96 }}
      transition={SPRING}
      className={`relative rounded-lg border p-4 ${styles.border} ${styles.bg}`}
    >
      <Button
        variant="ghost"
        size="icon-sm"
        {...animHandlers(xRef)}
        onClick={() => onDismiss(alert.id)}
        aria-label="Dismiss alert"
        className="absolute right-1.5 top-1.5 size-6"
      >
        <XIcon ref={xRef} size={14} />
      </Button>
      <div className="flex items-start gap-3 pr-6">
        <div
          className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${styles.iconBg} ${styles.iconColor}`}
        >
          <AlertBodyIcon variant={alert.variant} size={20} />
        </div>
        <div>
          <h3 className="text-sm font-semibold">{alert.title}</h3>
          <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
            {alert.message}
          </p>
          <Button variant="link" size="sm" className="mt-2 h-auto gap-1 px-0 text-xs">
            {alert.action}
          </Button>
        </div>
      </div>
    </motion.div>
  );
}

export default function DashboardPage() {
  const [alerts, setAlerts] = useState<Alert[]>(initialAlerts);
  // The grid follows `alertsOpen`, which only closes AFTER the last alert's
  // exit animation finishes (see AnimatePresence onExitComplete). This keeps the
  // column tall and in place while the alert fades out, so Quick Actions slides
  // down once (cleanly) instead of bouncing: slide-down then snap-up.
  const [alertsOpen, setAlertsOpen] = useState(initialAlerts.length > 0);
  const [isAlertsModalOpen, setIsAlertsModalOpen] = useState(false);

  const settingsRef = useRef<AnimIconHandle>(null);
  const guideRef = useRef<AnimIconHandle>(null);
  const addAlertRef = useRef<AnimIconHandle>(null);

  const visibleAlerts = alerts.slice(0, 3);
  const hiddenCount = Math.max(0, alerts.length - 3);
  const hasAlerts = alertsOpen;

  function dismissAlert(id: string) {
    setAlerts((prev) => prev.filter((alert) => alert.id !== id));
  }

  function spawnAlert() {
    const variants: Alert["variant"][] = ["warning", "error", "info"];
    const variant = variants[alerts.length % variants.length];
    const nextId = alerts.length + 1;
    const newAlert: Alert =
      variant === "error"
        ? {
            id: `alert-${nextId}`,
            variant,
            title: `Critical alert ${nextId}`,
            message: "A service reported multiple failed heartbeats.",
            action: "Investigate",
          }
        : variant === "info"
          ? {
              id: `alert-${nextId}`,
              variant,
              title: `Information ${nextId}`,
              message: "A new SDK version is available for this project.",
              action: "Read more",
            }
          : {
              id: `alert-${nextId}`,
              variant,
              title: `Action required ${nextId}`,
              message: "Review pending permissions for new project members.",
              action: "Review",
            };
    setAlerts((prev) => [...prev, newAlert]);
    setAlertsOpen(true);
  }

  return (
    <Stagger root className="mx-auto flex w-full max-w-7xl flex-1 flex-col">
      <PageHeader
        crumbs={["Projects", "Unknown project"]}
        title="Unknown project"
        description="Project control plane and integration overview."
        badge={
          <Badge className="gap-1.5 bg-emerald-500/15 text-emerald-700 hover:bg-emerald-500/15 dark:text-emerald-300">
            <span className="nexus-live relative h-1.5 w-1.5 rounded-full bg-emerald-500 text-emerald-500" />
            Active
          </Badge>
        }
        actions={
          <>
            <Button variant="outline" {...animHandlers(settingsRef)} className="gap-2">
              <SettingsIcon ref={settingsRef} size={16} />
              Project settings
            </Button>
            <Button {...animHandlers(guideRef)} className="gap-2">
              <BookTextIcon ref={guideRef} size={16} />
              Integration guide
            </Button>
          </>
        }
      />

      <Stagger className="mt-6 grid flex-1 grid-cols-12 items-start gap-6">
        {/* Project readiness: col-9 beside the alerts column, full-width
            without. `layout="position"` widens it without distorting content. */}
        <motion.div
          layout="position"
          className={cn(
            "col-span-12 lg:col-start-1 lg:row-start-1",
            hasAlerts ? "lg:col-span-9" : "lg:col-span-12",
          )}
        >
          <ProjectReadiness />
        </motion.div>

        {/* Side column: spans rows 1-2 with alerts so a tall alert list never
            leaves a gap beside readiness (no scrollbar needed). It collapses to
            row 2 once the last alert finishes exiting (onExitComplete).

            Both the column AND the Quick-actions group use `layout="position"`:
            when an alert leaves, the column slides down while Quick actions
            slides up within it — the two compose into one smooth downward
            glide instead of a jump-then-slide bounce. */}
        <motion.div
          layout="position"
          className={cn(
            "col-span-12 flex flex-col gap-6 lg:col-span-3 lg:col-start-10",
            hasAlerts ? "lg:row-start-1 lg:row-span-2" : "lg:row-start-2",
          )}
        >
          <div className="flex flex-col gap-3 empty:hidden">
            <AnimatePresence
              initial={false}
              onExitComplete={() => {
                if (alerts.length === 0) setAlertsOpen(false);
              }}
            >
              {visibleAlerts.map((alert) => (
                <AlertCard key={alert.id} alert={alert} onDismiss={dismissAlert} />
              ))}
            </AnimatePresence>

            {hiddenCount > 0 ? (
              <Button
                variant="outline"
                className="w-full tabular-nums"
                onClick={() => setIsAlertsModalOpen(true)}
              >
                View {hiddenCount} more alert{hiddenCount === 1 ? "" : "s"}
              </Button>
            ) : null}
          </div>

          <motion.div layout="position" className="flex flex-col gap-6">
            <QuickActions />

            <Button
              variant="outline"
              {...animHandlers(addAlertRef)}
              className="w-full border-dashed gap-1.5"
              onClick={spawnAlert}
            >
              <PlusIcon ref={addAlertRef} size={14} />
              Add test alert
            </Button>
          </motion.div>
        </motion.div>

        {/* Capability cards: always col-9 on the left at row 2. */}
        <div className="col-span-12 grid grid-cols-1 gap-6 lg:col-span-9 lg:col-start-1 lg:row-start-2 lg:grid-cols-2">
          <EnabledCapabilities />
          <Integration />
          <Access />
          <RecentActivity />
        </div>
      </Stagger>

      <Dialog open={isAlertsModalOpen} onOpenChange={setIsAlertsModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
            All alerts (<span className="tabular-nums">{alerts.length}</span>)
          </DialogTitle>
          </DialogHeader>
          <div className="flex max-h-[60vh] flex-col gap-3 overflow-y-auto">
            {alerts.map((alert) => {
              const styles = alertStyles[alert.variant];
              return (
                <div
                  key={alert.id}
                  className={`relative rounded-md border p-3 ${styles.border} ${styles.bg}`}
                >
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => dismissAlert(alert.id)}
                    aria-label="Dismiss alert"
                    className="absolute right-1.5 top-1.5 size-6"
                  >
                    <XIcon size={14} />
                  </Button>
                  <div className="flex items-start gap-3 pr-6">
                    <div
                      className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${styles.iconBg} ${styles.iconColor}`}
                    >
                      <AlertBodyIcon variant={alert.variant} size={16} />
                    </div>
                    <div>
                      <h3 className="text-sm font-semibold">{alert.title}</h3>
                      <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">
                        {alert.message}
                      </p>
                      <Button variant="link" size="sm" className="mt-1 h-auto gap-1 px-0 text-xs">
                        {alert.action}
                      </Button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </DialogContent>
      </Dialog>
    </Stagger>
  );
}
