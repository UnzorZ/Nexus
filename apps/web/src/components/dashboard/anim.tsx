"use client";

import type { ReactNode } from "react";
import { motion, useReducedMotion } from "motion/react";
import type { Transition, Variants } from "motion/react";

/* -------------------------------------------------------------------------- */
/*  Container-driven icon animation                                           */
/* -------------------------------------------------------------------------- */

/** Shape every animated icon exposes via its forwarded ref. */
export type AnimIconHandle = {
  startAnimation: () => void;
  stopAnimation: () => void;
};

/**
 * Hover handlers that drive an animated icon from its container. Create the ref
 * in the component (so the linter can trace it) and spread these on the element:
 *
 *   const iconRef = useRef<AnimIconHandle>(null);
 *   <Button {...animHandlers(iconRef)}><BellIcon ref={iconRef} /></Button>
 *
 * Attaching the ref switches the icon into "controlled" mode (it stops
 * self-animating), so it plays exactly when the container is hovered.
 */
export function animHandlers(ref: { current: AnimIconHandle | null }) {
  return {
    // Defensive: some icon refs may forward a DOM node instead of the
    // AnimIconHandle imperative API. Guard so one mis-wired ref can't throw
    // "startAnimation is not a function" on hover.
    onMouseEnter: () => {
      const h = ref.current;
      if (h && typeof h.startAnimation === "function") h.startAnimation();
    },
    onMouseLeave: () => {
      const h = ref.current;
      if (h && typeof h.stopAnimation === "function") h.stopAnimation();
    },
  };
}

/* -------------------------------------------------------------------------- */
/*  Timing tokens                                                             */
/* -------------------------------------------------------------------------- */

export const SPRING: Transition = {
  type: "spring",
  stiffness: 380,
  damping: 30,
  mass: 0.8,
};
export const SPRING_SNAPPY: Transition = {
  type: "spring",
  stiffness: 520,
  damping: 34,
};
export const EASE_OUT: [number, number, number, number] = [0.22, 1, 0.36, 1];

/* -------------------------------------------------------------------------- */
/*  Dark-aware accent tints (icon chips, status pills)                        */
/* -------------------------------------------------------------------------- */

/** Light pastel chip in light mode, translucent vivid chip in dark mode. */
export const tint = {
  indigo: { bg: "bg-indigo-50 dark:bg-indigo-500/15", text: "text-indigo-600 dark:text-indigo-300" },
  violet: { bg: "bg-violet-50 dark:bg-violet-500/15", text: "text-violet-600 dark:text-violet-300" },
  cyan: { bg: "bg-cyan-50 dark:bg-cyan-500/15", text: "text-cyan-600 dark:text-cyan-300" },
  amber: { bg: "bg-amber-50 dark:bg-amber-500/15", text: "text-amber-600 dark:text-amber-300" },
  emerald: { bg: "bg-emerald-50 dark:bg-emerald-500/15", text: "text-emerald-600 dark:text-emerald-300" },
  red: { bg: "bg-red-50 dark:bg-red-500/15", text: "text-red-600 dark:text-red-300" },
  blue: { bg: "bg-blue-50 dark:bg-blue-500/15", text: "text-blue-600 dark:text-blue-300" },
} as const;

/* -------------------------------------------------------------------------- */
/*  Entrance variants                                                         */
/* -------------------------------------------------------------------------- */

export const staggerContainer: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.06, delayChildren: 0.04 } },
};

export const fadeUp: Variants = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { duration: 0.45, ease: EASE_OUT } },
};

const fadeUpReduced: Variants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { duration: 0.2 } },
};

type DivProps = {
  className?: string;
  children: ReactNode;
};

/** Staggered entrance container. Use `root` on the top-level instance. */
export function Stagger({
  className,
  children,
  root = false,
}: DivProps & { root?: boolean }) {
  return (
    <motion.div
      className={className}
      variants={staggerContainer}
      initial={root ? "hidden" : undefined}
      animate={root ? "show" : undefined}
    >
      {children}
    </motion.div>
  );
}

/** Motion wrapper for staggered fade-up entrance (no hover effect). */
export function MotionCard({ className, children }: DivProps) {
  const reduce = useReducedMotion();
  return (
    <motion.div className={className} variants={reduce ? fadeUpReduced : fadeUp}>
      {children}
    </motion.div>
  );
}
