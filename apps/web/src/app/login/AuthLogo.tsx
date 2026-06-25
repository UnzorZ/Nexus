"use client";

import Link from "next/link";
import Image from "next/image";
import { useTheme } from "next-themes";
import { useMounted } from "@/components/ui/theme-toggle";
import { cn } from "@/lib/utils";

/**
 * Real Nexus brand lockup for auth pages. Uses the full wordmark logo on light
 * surfaces (where its near-black wordmark is legible) and the theme-adaptive
 * rings lockup on dark surfaces.
 */
export function AuthLogo({
  height = 28,
  className,
  asLink = true,
}: {
  height?: number;
  className?: string;
  asLink?: boolean;
}) {
  const { resolvedTheme } = useTheme();
  const mounted = useMounted();
  const dark = mounted && resolvedTheme === "dark";

  const inner = dark ? (
    <span className={cn("inline-flex items-center gap-2.5", className)}>
      <Image
        src="/nexus-logo-icon.png"
        alt="Nexus"
        width={Math.round(height * (320 / 272))}
        height={height}
        priority
        className="h-auto w-auto"
      />
      <span className="font-mono text-[0.9rem] font-semibold tracking-[0.24em] text-foreground">
        NEXUS
      </span>
    </span>
  ) : (
    <Image
      src="/nexus-logo.png"
      alt="Nexus"
      width={Math.round(height * (1200 / 272))}
      height={height}
      priority
      className={cn("h-auto w-auto", className)}
    />
  );

  if (!asLink) return inner;
  return (
    <Link href="/" aria-label="Nexus home" className="inline-flex">
      {inner}
    </Link>
  );
}
