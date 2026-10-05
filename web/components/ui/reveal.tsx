"use client";

import { motion, useReducedMotion } from "motion/react";
import type { ReactNode } from "react";

/**
 * Scroll reveal, isolated so the rest of the page can stay a Server Component.
 *
 * A 12px rise and a fade over 520ms on the app's own `CalmEasing` curve
 * (`cubic-bezier(0.4, 0, 0.2, 1)`, copied from `MotionTokens`), so a CSS
 * transition elsewhere on the same screen settles on the same frame.
 *
 * `useReducedMotion` drops the travel and shortens the fade rather than
 * removing the reveal outright, because a screen that never reveals at all is
 * more disorienting than one that simply appears.
 */
export function Reveal({
  children,
  delay = 0,
  className,
}: {
  children: ReactNode;
  delay?: number;
  className?: string;
}) {
  const reduced = useReducedMotion();

  if (reduced) {
    return <div className={className}>{children}</div>;
  }

  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: 12 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-15%" }}
      transition={{ duration: 0.52, delay, ease: [0.4, 0, 0.2, 1] }}
    >
      {children}
    </motion.div>
  );
}