"use client";

import { use } from "react";
import Link from "next/link";
import { motion } from "motion/react";
import { Check, ShieldCheck } from "lucide-react";
import { AuthShell } from "@/app/login/AuthShell";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";

const SCOPES = [
  "View your account profile and email",
  "Access project resources on your behalf",
  "Maintain session and webhook integrity",
];

export default function OAuthLoginPage({
  params,
}: {
  params: Promise<{ slug: string }>;
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { slug } = use(params);

  return (
    <AuthShell mode="minimal">
      <motion.header variants={fadeUp} initial="hidden" animate="show">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
          Authorize request
        </h1>
        <p className="mt-1.5 text-sm text-muted-foreground">
          An application wants to access the{" "}
          <strong className="font-semibold text-foreground">{slug}</strong>{" "}
          project on your behalf.
        </p>
      </motion.header>

      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={SPRING_SNAPPY}
        className="mt-6"
      >
        <Card>
          <CardHeader>
            <CardTitle className="font-heading text-sm">
              This application will be able to:
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-2.5">
              {SCOPES.map((scope) => (
                <li
                  key={scope}
                  className="flex items-start gap-2.5 text-sm text-muted-foreground"
                >
                  <Check
                    size={16}
                    className="mt-0.5 shrink-0 text-emerald-600 dark:text-emerald-400"
                  />
                  <span>{scope}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </motion.div>

      <p className="mt-4 text-xs leading-relaxed text-muted-foreground">
        This does not give the application your Nexus password or session
        credentials. You can revoke access at any time from your project
        settings.
      </p>

      <form className="mt-5 flex gap-3">
        <Button asChild variant="outline" className="h-11 flex-1">
          <Link href="/login">Cancel</Link>
        </Button>
        <Button type="submit" className="h-11 flex-1" disabled>
          Authorize
        </Button>
      </form>

      <div className="mt-6 flex items-center justify-center gap-2 text-xs text-muted-foreground">
        <ShieldCheck size={14} className="shrink-0" />
        <span>
          Signing in to{" "}
          <strong className="font-medium text-foreground">{slug}</strong> via
          Nexus identity.
        </span>
      </div>
    </AuthShell>
  );
}
