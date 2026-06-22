import Image from "next/image";
import Link from "next/link";
import { cn } from "@/lib/utils";

/**
 * The Nexus logo, two ways:
 * - `full` — the real brand lockup `/nexus-logo.png` (transparent; the rings
 *   mark + the styled "NEXUS" wordmark with its cyan highlight). Use on LIGHT
 *   surfaces — the wordmark is dark and disappears on dark backgrounds.
 * - `lockup` (default) — the rings icon `/nexus-logo-icon.png` + a light,
 *   tracked "NEXUS" wordmark of our own. Use on DARK surfaces (v1 landing),
 *   where the full logo's dark wordmark would be invisible.
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
      <span className={cn("nx-logo", className)}>
        <Image
          src="/nexus-logo-icon.png"
          alt="Nexus"
          width={Math.round(height * (320 / 272))}
          height={height}
          priority
          className="nx-logo__mark"
        />
        {withText ? <span className="nx-logo__text">NEXUS</span> : null}
      </span>
    );

  if (!asLink) return inner;
  return (
    <Link href="/" className="landing-wordmark-link" aria-label="Nexus home">
      {inner}
    </Link>
  );
}
