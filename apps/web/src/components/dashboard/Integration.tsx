"use client";

import { useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Check } from "lucide-react";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { CopyIcon } from "@/components/ui/copy";
import { MotionCard, SPRING_SNAPPY, animHandlers, type AnimIconHandle } from "./anim";

const integrationData = {
  sdk: "Spring Boot starter",
  application: "f-shop-api",
  version: "1.4.2",
  lastHeartbeat: "42 seconds ago",
  apiKey: "nx_live_**********a1b2",
};

const configSnippet = `nexus:
  project: f-shop
  api-key: \${NEXUS_API_KEY}`;

export function Integration() {
  const [copied, setCopied] = useState(false);
  const copyRef = useRef<AnimIconHandle>(null);

  async function copyApiKey() {
    try {
      await navigator.clipboard?.writeText(integrationData.apiKey);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      /* clipboard unavailable — ignore */
    }
  }

  return (
    <MotionCard className="h-full">
      <Card className="h-full">
        <CardHeader>
          <CardTitle>Integration</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col gap-2.5 text-sm">
            <Row label="SDK" value={integrationData.sdk} />
            <Row label="Application" value={integrationData.application} />
            <Row label="Version" value={integrationData.version} />
            <div className="flex items-center gap-3">
              <span className="w-24 shrink-0 text-xs text-muted-foreground">
                Last heartbeat
              </span>
              <span className="flex items-center gap-1.5 font-medium">
                <span className="nexus-live relative h-2 w-2 rounded-full bg-emerald-500 text-emerald-500" />
                {integrationData.lastHeartbeat}
              </span>
            </div>
            <div className="flex items-center gap-3">
              <span className="w-24 shrink-0 text-xs text-muted-foreground">
                API key
              </span>
              <span className="flex min-w-0 flex-1 items-center gap-2 font-medium">
                <span className="truncate">{integrationData.apiKey}</span>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={copied ? "Copied" : "Copy API key"}
                  onClick={copyApiKey}
                  {...animHandlers(copyRef)}
                  className="relative shrink-0"
                >
                  <AnimatePresence mode="wait" initial={false}>
                    {copied ? (
                      <motion.span
                        key="check"
                        initial={{ opacity: 0, scale: 0.4 }}
                        animate={{ opacity: 1, scale: 1 }}
                        exit={{ opacity: 0, scale: 0.4 }}
                        transition={SPRING_SNAPPY}
                      >
                        <Check className="size-3.5 text-emerald-600" />
                      </motion.span>
                    ) : (
                      <motion.span
                        key="copy"
                        initial={{ opacity: 0, scale: 0.4 }}
                        animate={{ opacity: 1, scale: 1 }}
                        exit={{ opacity: 0, scale: 0.4 }}
                        transition={SPRING_SNAPPY}
                      >
                        <CopyIcon ref={copyRef} size={14} />
                      </motion.span>
                    )}
                  </AnimatePresence>
                </Button>
              </span>
            </div>
          </div>

          <pre className="mt-4 w-full overflow-x-auto rounded-md bg-muted p-3 font-mono text-[11px] leading-relaxed text-muted-foreground">
            {configSnippet}
          </pre>
        </CardContent>
        <CardFooter className="border-t">
          <Button variant="secondary" size="sm" className="ml-auto">
            View credentials
          </Button>
        </CardFooter>
      </Card>
    </MotionCard>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center gap-3">
      <span className="w-24 shrink-0 text-xs text-muted-foreground">
        {label}
      </span>
      <span className="font-medium">{value}</span>
    </div>
  );
}
