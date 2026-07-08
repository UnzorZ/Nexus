"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Suspense, use, useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "motion/react";
import { MonitorSmartphone, ShieldAlert } from "lucide-react";
import { NexusApiError } from "@/lib/api/client";
import {
  fetchEndUserMe,
  fetchEndUserSessions,
  revokeAllEndUserSessions,
  revokeEndUserSession,
  type EndUserSessionSummary,
} from "@/features/end-user/api";
import { queryKeys } from "@/lib/api/queryKeys";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { Button } from "@/components/ui/button";
import { AuthShell } from "@/app/login/AuthShell";

function formatInstant(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function describeUserAgent(userAgent: string | null | undefined): string {
  if (!userAgent || userAgent.trim() === "") {
    return "Unknown device or browser";
  }
  return userAgent;
}

export default function EndUserSessionsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  return (
    <Suspense fallback={<AuthShell mode="minimal" />}>
      <SessionsScreen slugPromise={params} />
    </Suspense>
  );
}

function SessionsScreen({
  slugPromise,
}: {
  slugPromise: Promise<{ slug: string }>;
}) {
  const { slug } = use(slugPromise);
  const router = useRouter();
  const qc = useQueryClient();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetchEndUserMe(slug).then((me) => {
      if (cancelled) return;
      if (!me) {
        router.replace(`/p/${encodeURIComponent(slug)}/login`);
        return;
      }
      setReady(true);
    });
    return () => {
      cancelled = true;
    };
  }, [slug, router]);

  const sessions = useQuery({
    queryKey: queryKeys.endUser.sessions(slug),
    queryFn: () => fetchEndUserSessions(slug),
    enabled: ready,
  });

  const revokeM = useMutation({
    mutationFn: (sessionId: string) => revokeEndUserSession(slug, sessionId),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.endUser.sessions(slug) }),
  });

  const revokeAllM = useMutation({
    mutationFn: () => revokeAllEndUserSessions(slug),
  });

  async function handleRevoke(session: EndUserSessionSummary) {
    try {
      await revokeM.mutateAsync(session.id);
      if (session.current) {
        router.replace(`/p/${encodeURIComponent(slug)}/login`);
      }
    } catch {
      /* el error se expone vía revokeM.error */
    }
  }

  async function handleRevokeAll() {
    try {
      await revokeAllM.mutateAsync();
      router.replace(`/p/${encodeURIComponent(slug)}/login`);
    } catch {
      /* el error se expone vía revokeAllM.error */
    }
  }

  const error =
    (sessions.error instanceof NexusApiError && sessions.error.status !== 401
      ? sessions.error.message
      : null) ??
    (revokeM.error instanceof NexusApiError ? revokeM.error.message : null) ??
    (revokeAllM.error instanceof NexusApiError ? revokeAllM.error.message : null);

  if (!ready) {
    return (
      <AuthShell mode="minimal">
        <motion.p variants={fadeUp} initial="hidden" animate="show" className="text-sm text-muted-foreground">
          Loading…
        </motion.p>
      </AuthShell>
    );
  }

  const list = sessions.data ?? [];
  const pendingId = revokeM.isPending ? revokeM.variables ?? null : null;

  return (
    <AuthShell mode="minimal">
      <motion.header variants={fadeUp} initial="hidden" animate="show">
        <div className="flex items-center gap-2 text-violet-600 dark:text-violet-400">
          <MonitorSmartphone size={18} />
          <span className="text-sm font-medium">Sessions</span>
        </div>
        <h1 className="mt-2 text-2xl font-bold tracking-tight text-foreground">
          Your active sessions
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Sign out of any device, or everywhere at once.
        </p>
      </motion.header>

      {error ? (
        <motion.div
          role="alert"
          initial={{ opacity: 0, y: -4 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
          className="mt-5 flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive"
        >
          <ShieldAlert size={16} className="shrink-0" />
          <span>{error}</span>
        </motion.div>
      ) : null}

      <Button
        type="button"
        variant="outline"
        className="mt-6 h-11 w-full"
        onClick={handleRevokeAll}
        disabled={revokeAllM.isPending || pendingId !== null || list.length === 0}
      >
        {revokeAllM.isPending ? "Signing out everywhere…" : "Sign out everywhere"}
      </Button>

      <ul className="mt-5 space-y-2.5">
        {list.map((session) => (
          <motion.li
            key={session.id}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            transition={SPRING_SNAPPY}
            className="rounded-xl border border-border bg-card p-4"
          >
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  {session.current ? (
                    <span className="rounded-full border border-violet-500/30 bg-violet-500/10 px-2 py-0.5 text-xs font-medium text-violet-700 dark:text-violet-300">
                      This device
                    </span>
                  ) : null}
                  <span className="text-xs text-muted-foreground">
                    {describeUserAgent(session.userAgent)}
                  </span>
                </div>
                <dl className="grid grid-cols-1 gap-x-6 gap-y-0.5 text-xs text-muted-foreground sm:grid-cols-2">
                  <div>
                    <span className="font-medium text-foreground/70">Signed in: </span>
                    {formatInstant(session.createdAt)}
                  </div>
                  <div>
                    <span className="font-medium text-foreground/70">Last seen: </span>
                    {formatInstant(session.lastAccessedAt)}
                  </div>
                  <div>
                    <span className="font-medium text-foreground/70">Expires: </span>
                    {formatInstant(session.expiresAt)}
                  </div>
                </dl>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                onClick={() => handleRevoke(session)}
                disabled={pendingId !== null || revokeAllM.isPending}
              >
                {pendingId === session.id ? "Revoking…" : "Revoke"}
              </Button>
            </div>
          </motion.li>
        ))}
        {list.length === 0 && !error ? (
          <li className="rounded-xl border border-dashed border-border bg-card/50 p-6 text-center text-sm text-muted-foreground">
            No active sessions.
          </li>
        ) : null}
      </ul>

      <Link
        href={`/p/${encodeURIComponent(slug)}/account`}
        className="mt-6 block text-center text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        ← Back to account
      </Link>
    </AuthShell>
  );
}
