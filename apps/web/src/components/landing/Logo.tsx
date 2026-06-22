import Image from "next/image";
import Link from "next/link";
import { cn } from "@/lib/utils";

/**
 * The Nexus logo, two variants — pick by surface brightness:
 * - `full` — the real brand lockup `/nexus-logo.png` (transparent; rings mark
 *   + the styled "NEXUS" wordmark with its cyan highlight). The wordmark is
 *   near-black, so use this on LIGHT surfaces only.
 * - `lockup` — the rings icon `/nexus-logo-icon.png` + a `text-foreground`
 *   "NEXUS" wordmark of our own. Adapts to any surface (use on dark, or when
 *   you need the wordmark to follow the theme).
 *
 * Self-contained (Tailwind only) — does not depend on any landing stylesheet.
 */
export function Logo({
  variant = "lockup",
  height = 28,
  className,
  asLink = true,
  withText = true,
}: {
  variant?: "lockup" | "full";
  height?: number;
  className?: string;
  asLink?: boolean;
  withText?: boolean;
}) {
  const inner =
    variant === "full" ? (
      <Image
        src="/nexus-logo.png"
        alt="Nexus"
        width={Math.round(height * (1200 / 272))}
        height={height}
        priority
        className={cn("h-auto w-auto", className)}
      />
    ) : (
      <span className={cn("inline-flex items-center gap-2.5", className)}>
        <Image
          src="/nexus-logo-icon.png"
          alt="Nexus"
          width={Math.round(height * (320 / 272))}
          height={height}
          priority
          className="h-auto w-auto"
        />
        {withText ? (
          <span className="font-mono text-[0.9rem] font-semibold tracking-[0.24em] text-foreground">
            NEXUS
          </span>
        ) : null}
      </span>
    );

  if (!asLink) return inner;
  return (
    <Link href="/" aria-label="Nexus home" className="inline-flex">
      {inner}
    </Link>
  );
}
