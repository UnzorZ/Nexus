"use client";

import Link from "next/link";
import { motion } from "motion/react";
import { ShieldAlert } from "lucide-react";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { Button } from "@/components/ui/button";
import { AuthShell } from "../AuthShell";

export default function AccountSuspendedPage() {
  return (
    <AuthShell mode="login">
      <motion.div
        initial={{ opacity: 0, y: -4 }}
        animate={{ opacity: 1, y: 0 }}
        transition={SPRING_SNAPPY}
        className="mt-2 flex items-center gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-700 dark:text-amber-300"
        role="status"
      >
        <ShieldAlert size={16} className="shrink-0" />
        <span>Esta cuenta está suspendida.</span>
      </motion.div>

      <motion.header variants={fadeUp} initial="hidden" animate="show" className="mt-5">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
          Cuenta suspendida
        </h1>
        <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
          Tu cuenta de Nexus está suspendida o desactivada y no puede iniciar
          sesión. Si crees que se trata de un error, contacta con el administrador
          de la instancia.
        </p>
      </motion.header>

      <motion.div variants={fadeUp} initial="hidden" animate="show" className="mt-6">
        <Button asChild className="h-11 w-full">
          <Link href="/login">Volver al inicio de sesión</Link>
        </Button>
      </motion.div>
    </AuthShell>
  );
}
