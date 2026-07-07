"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, Suspense, use, useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { QRCodeSVG } from "qrcode.react";
import { motion } from "motion/react";
import { Copy, KeyRound, ShieldAlert, ShieldCheck } from "lucide-react";
import { NexusApiError } from "@/lib/api/client";
import {
  beginEndUserMfaEnrollment,
  confirmEndUserMfaEnrollment,
  disableEndUserMfa,
  fetchEndUserMe,
  fetchEndUserMfaStatus,
  type EndUserMfaEnrollment,
} from "@/features/end-user/api";
import { queryKeys } from "@/lib/api/queryKeys";
import { fadeUp, SPRING_SNAPPY } from "@/components/dashboard/anim";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AuthShell } from "@/app/login/AuthShell";

type Step = "idle" | "qr" | "recovery" | "enabled";

export default function EndUserSecurityPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  return (
    <Suspense fallback={<AuthShell mode="minimal" />}>
      <SecurityScreen slugPromise={params} />
    </Suspense>
  );
}

function SecurityScreen({
  slugPromise,
}: {
  slugPromise: Promise<{ slug: string }>;
}) {
  const { slug } = use(slugPromise);
  const router = useRouter();
  const qc = useQueryClient();
  const [ready, setReady] = useState(false);
  const [step, setStep] = useState<Step>("idle");
  const [enrollment, setEnrollment] = useState<EndUserMfaEnrollment | null>(null);
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const status = useQuery({
    queryKey: queryKeys.endUser.mfaStatus(slug),
    queryFn: () => fetchEndUserMfaStatus(slug),
    enabled: ready,
  });

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

  const enrollM = useMutation({
    mutationFn: () => beginEndUserMfaEnrollment(slug),
    onSuccess: (e) => {
      setEnrollment(e);
      setStep("qr");
      setError(null);
    },
    onError: (err) =>
      setError(err instanceof NexusApiError ? err.message : "Could not start enrollment."),
  });

  const verifyM = useMutation({
    mutationFn: (code: string) => confirmEndUserMfaEnrollment(slug, code),
    onSuccess: (codes) => {
      setRecoveryCodes(codes);
      setStep("recovery");
      setError(null);
      qc.invalidateQueries({ queryKey: queryKeys.endUser.mfaStatus(slug) });
    },
    onError: (err) =>
      setError(err instanceof NexusApiError ? err.message : "Invalid code."),
  });

  const disableM = useMutation({
    mutationFn: (code: string) => disableEndUserMfa(slug, code),
    onSuccess: () => {
      setStep("idle");
      setError(null);
      qc.invalidateQueries({ queryKey: queryKeys.endUser.mfaStatus(slug) });
    },
    onError: (err) =>
      setError(err instanceof NexusApiError ? err.message : "Could not disable MFA."),
  });

  async function handleVerify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const code = String(new FormData(event.currentTarget).get("code") ?? "").trim();
    if (code) await verifyM.mutateAsync(code);
  }
  async function handleDisable(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const code = String(new FormData(event.currentTarget).get("code") ?? "").trim();
    if (code) await disableM.mutateAsync(code);
  }

  if (!ready || !status.data) {
    return (
      <AuthShell mode="minimal">
        <motion.p variants={fadeUp} initial="hidden" animate="show" className="text-sm text-muted-foreground">
          Loading…
        </motion.p>
      </AuthShell>
    );
  }

  const enabled = status.data.enabled || step === "recovery";

  return (
    <AuthShell mode="minimal">
      <motion.header variants={fadeUp} initial="hidden" animate="show">
        <div className="flex items-center gap-2 text-violet-600 dark:text-violet-400">
          <KeyRound size={18} />
          <span className="text-sm font-medium">Security</span>
        </div>
        <h1 className="mt-2 text-2xl font-bold tracking-tight text-foreground">
          Two-factor authentication
        </h1>
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

      {enabled && step !== "recovery" ? (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={SPRING_SNAPPY}
          className="mt-6 space-y-4"
        >
          <div className="flex items-center gap-2 rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-sm text-emerald-700 dark:text-emerald-300">
            <ShieldCheck size={16} className="shrink-0" />
            <span>
              Two-factor is on. Recovery codes remaining: {status.data.recoveryCodesRemaining}.
            </span>
          </div>
          <form onSubmit={handleDisable} className="space-y-3">
            <Label htmlFor="dcode" className="text-sm font-medium">
              Disable two-factor (enter a code to confirm)
            </Label>
            <Input
              id="dcode"
              name="code"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              className="h-12 bg-white px-3 focus-visible:ring-violet-500/30 dark:bg-input/30"
              placeholder="000000"
              disabled={disableM.isPending}
            />
            <Button type="submit" variant="outline" className="h-11 w-full" disabled={disableM.isPending}>
              {disableM.isPending ? "Disabling…" : "Disable two-factor"}
            </Button>
          </form>
        </motion.div>
      ) : step === "qr" && enrollment ? (
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={SPRING_SNAPPY} className="mt-6 space-y-5">
          <p className="text-sm text-muted-foreground">
            Scan this QR with your authenticator app (Google Authenticator, Authy, 1Password…),
            then enter the 6-digit code it shows.
          </p>
          <div className="flex justify-center rounded-xl border border-border bg-white p-4 dark:bg-background">
            <QRCodeSVG value={enrollment.otpauthUri} size={192} />
          </div>
          <details className="text-xs text-muted-foreground">
            <summary className="cursor-pointer">Can&apos;t scan? Enter this key manually</summary>
            <p className="mt-2 break-all font-mono">{enrollment.secret}</p>
          </details>
          <form onSubmit={handleVerify} className="space-y-3">
            <Label htmlFor="code" className="text-sm font-medium">Verification code</Label>
            <Input
              id="code"
              name="code"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              className="h-12 bg-white px-3 text-center text-lg tracking-[0.4em] focus-visible:ring-violet-500/30 dark:bg-input/30"
              placeholder="000000"
              autoFocus
              disabled={verifyM.isPending}
            />
            <Button type="submit" className="h-12 w-full rounded-xl bg-violet-600 text-white hover:bg-violet-700 dark:bg-violet-500" disabled={verifyM.isPending}>
              {verifyM.isPending ? "Verifying…" : "Verify and enable"}
            </Button>
          </form>
        </motion.div>
      ) : step === "recovery" && recoveryCodes ? (
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={SPRING_SNAPPY} className="mt-6 space-y-4">
          <div className="flex items-center gap-2 rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-sm text-emerald-700 dark:text-emerald-300">
            <ShieldCheck size={16} className="shrink-0" />
            <span>Two-factor is now enabled.</span>
          </div>
          <p className="text-sm text-muted-foreground">
            Save these one-time recovery codes somewhere safe. Each can be used once instead of a
            TOTP code if you lose your device.
          </p>
          <div className="grid grid-cols-2 gap-1.5 rounded-xl border border-border bg-card p-4 font-mono text-sm">
            {recoveryCodes.map((c) => (
              <div key={c} className="flex items-center gap-1.5">
                <button
                  type="button"
                  className="text-muted-foreground hover:text-foreground"
                  title="Copy"
                  onClick={() => navigator.clipboard?.writeText(c)}
                >
                  <Copy size={13} />
                </button>
                <span>{c}</span>
              </div>
            ))}
          </div>
          <Button className="h-11 w-full" onClick={() => { setStep("enabled"); setRecoveryCodes(null); }}>
            I&apos;ve saved my recovery codes
          </Button>
        </motion.div>
      ) : (
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={SPRING_SNAPPY} className="mt-6 space-y-4">
          <p className="text-sm text-muted-foreground">
            Add a second factor (TOTP authenticator app) to protect your account. After enabling,
            you&apos;ll need a code from your app each time you sign in.
          </p>
          <Button
            className="h-12 w-full rounded-xl bg-violet-600 text-white hover:bg-violet-700 dark:bg-violet-500"
            disabled={enrollM.isPending}
            onClick={() => enrollM.mutate()}
          >
            {enrollM.isPending ? "Starting…" : "Set up two-factor"}
          </Button>
        </motion.div>
      )}

      <Link
        href={`/p/${encodeURIComponent(slug)}/account`}
        className="mt-6 block text-center text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        ← Back to account
      </Link>
    </AuthShell>
  );
}
