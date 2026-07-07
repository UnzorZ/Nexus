"use client";

import { motion } from "motion/react";
import { ShieldAlert } from "lucide-react";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { AuthShell } from "@/app/login/AuthShell";

/**
 * Mostrada cuando una petición a un endpoint global del Authorization Server (sin
 * proyecto) llega sin autenticación y no puede reanudarse. Sustituye al
 * {@code OAuthAuthenticationRequiredController} Thymeleaf.
 */
export default function AuthenticationRequiredPage() {
  return (
    <AuthShell mode="minimal">
      <motion.div
        variants={fadeUp}
        initial="hidden"
        animate="show"
        transition={SPRING_SNAPPY}
        className="flex flex-col items-center text-center"
      >
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-amber-500/10">
          <ShieldAlert className="text-amber-600 dark:text-amber-400" />
        </div>
        <h1 className="mt-4 text-2xl font-bold tracking-tight text-foreground">
          Authentication required
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          You need to sign in to continue. If you reached this page from an application,
          try starting the sign-in flow again.
        </p>
      </motion.div>
    </AuthShell>
  );
}
