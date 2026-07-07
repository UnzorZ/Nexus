"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Suspense, use, useEffect, useState } from "react";
import { motion } from "motion/react";
import { CheckCircle2, LogOut, ShieldCheck } from "lucide-react";
import { logoutEndUser, ProjectUserDetails } from "@/features/end-user/api";
import { fetchEndUserMe } from "@/features/end-user/api";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { Button } from "@/components/ui/button";
import { AuthShell } from "@/app/login/AuthShell";

/**
 * Portal mínimo del usuario final (landing post-login cuando no hay flujo OAuth que
 * reanudar). Comprueba la sesión contra {@code GET /api/p/{slug}/me}; si no hay sesión,
 * redirige al login. El portal completo (perfil editable, sesiones, etc.) llega más
 * adelante; esta página demuestra que la sesión JSON-login es reconocida por el backend.
 */
export default function EndUserAccountPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  return (
    <Suspense fallback={<AuthShell mode="minimal" />}>
      <EndUserAccountScreen slugPromise={params} />
    </Suspense>
  );
}

function EndUserAccountScreen({
  slugPromise,
}: {
  slugPromise: Promise<{ slug: string }>;
}) {
  const { slug } = use(slugPromise);
  const router = useRouter();
  const [me, setMe] = useState<ProjectUserDetails | null | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    fetchEndUserMe(slug).then((result) => {
      if (cancelled) return;
      if (!result) {
        router.replace(`/p/${encodeURIComponent(slug)}/login`);
        return;
      }
      setMe(result);
    });
    return () => {
      cancelled = true;
    };
  }, [slug, router]);

  async function handleLogout() {
    await logoutEndUser(slug);
    router.replace(`/p/${encodeURIComponent(slug)}/login`);
  }

  if (!me) {
    return (
      <AuthShell mode="minimal">
        <motion.p
          variants={fadeUp}
          initial="hidden"
          animate="show"
          className="text-sm text-muted-foreground"
        >
          Loading…
        </motion.p>
      </AuthShell>
    );
  }

  return (
    <AuthShell mode="minimal">
      <motion.header variants={fadeUp} initial="hidden" animate="show">
        <div className="flex items-center gap-2 text-emerald-600 dark:text-emerald-400">
          <CheckCircle2 size={18} />
          <span className="text-sm font-medium">Signed in</span>
        </div>
        <h1 className="mt-2 text-2xl font-bold tracking-tight text-foreground">
          {me.displayName ?? me.email}
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {me.email} · {slug}
        </p>
      </motion.header>

      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={SPRING_SNAPPY}
        className="mt-6 rounded-xl border border-border bg-card p-5"
      >
        <dl className="space-y-2 text-sm">
          <div className="flex justify-between">
            <dt className="text-muted-foreground">Status</dt>
            <dd className="font-medium text-foreground">{me.status}</dd>
          </div>
          {me.username ? (
            <div className="flex justify-between">
              <dt className="text-muted-foreground">Username</dt>
              <dd className="font-medium text-foreground">{me.username}</dd>
            </div>
          ) : null}
          <div className="flex justify-between">
            <dt className="text-muted-foreground">Two-factor</dt>
            <dd className="font-medium text-foreground">
              {me.mfaEnabled ? "On" : "Off"}
            </dd>
          </div>
        </dl>
      </motion.div>

      <div className="mt-6 space-y-2.5">
        <Button asChild variant="outline" className="h-11 w-full">
          <Link href={`/p/${encodeURIComponent(slug)}/account/security`}>
            <ShieldCheck size={16} />
            Two-factor authentication
          </Link>
        </Button>
        <Button
          variant="outline"
          className="h-11 w-full"
          onClick={handleLogout}
        >
          <LogOut size={16} />
          Sign out
        </Button>
      </div>

      <p className="mt-6 text-center text-sm text-muted-foreground">
        Manage more from your{" "}
        <Link
          href="/projects"
          className="font-medium text-violet-600 hover:underline dark:text-violet-400"
        >
          project dashboard
        </Link>
        .
      </p>
    </AuthShell>
  );
}
