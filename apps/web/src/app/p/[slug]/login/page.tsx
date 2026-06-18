"use client";

import { use } from "react";
import Link from "next/link";
import { motion } from "motion/react";
import { ArrowLeft, Shield, ShieldCheck } from "lucide-react";
import Image from "next/image";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { SPRING_SNAPPY } from "@/components/dashboard/anim";

export default function OAuthLoginPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { slug } = use(params);

  return (
    <main className="flex min-h-screen flex-col bg-muted/40">
      <header className="flex h-16 items-center border-b bg-card px-6">
        <div className="flex items-center gap-3">
          <Image
            src="/nexus-logo-icon.png"
            alt="Nexus"
            width={32}
            height={32}
            className="h-8 w-auto"
            priority
          />
          <span className="text-lg font-semibold tracking-tight">NEXUS</span>
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-lg flex-1 flex-col justify-center px-6 py-10">
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
        >
          <Link
            href="/login"
            className="mb-6 inline-flex items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
          >
            <ArrowLeft size={14} />
            Back to sign in
          </Link>

          <Card>
            <CardHeader className="text-center">
              <div className="mx-auto mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-primary/10">
                <Shield size={24} className="text-primary" />
              </div>
              <CardTitle className="text-xl">Authorization required</CardTitle>
              <CardDescription>
                A third-party application wants to access your Nexus account
                through the <span className="font-medium text-foreground">{slug}</span>{" "}
                project.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="rounded-lg border bg-card p-4">
                <h3 className="text-sm font-medium">
                  This application will be able to:
                </h3>
                <ul className="mt-3 flex flex-col gap-2 text-sm text-muted-foreground">
                  <li className="flex items-start gap-2">
                    <ShieldCheck size={16} className="mt-0.5 shrink-0 text-emerald-500" />
                    <span>View your account profile and email</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <ShieldCheck size={16} className="mt-0.5 shrink-0 text-emerald-500" />
                    <span>Access project resources on your behalf</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <ShieldCheck size={16} className="mt-0.5 shrink-0 text-emerald-500" />
                    <span>Maintain session and webhook integrity</span>
                  </li>
                </ul>
              </div>

              <p className="text-xs text-muted-foreground">
                This does not give the application access to your Nexus password
                or session credentials. You can revoke access at any time from
                your project settings.
              </p>
            </CardContent>
            <CardFooter className="flex-col gap-3">
              <div className="flex w-full gap-3">
                <Button variant="outline" className="flex-1" asChild>
                  <Link href="/login">Cancel</Link>
                </Button>
                <Button className="flex-1 gap-2">
                  <ShieldCheck size={16} />
                  Authorize
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">
                Signing in to <span className="font-medium text-foreground">{slug}</span>{" "}
                via Nexus identity.
              </p>
            </CardFooter>
          </Card>
        </motion.div>
      </div>
    </main>
  );
}
