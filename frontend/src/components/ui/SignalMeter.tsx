"use client";

import { motion, useReducedMotion } from "framer-motion";
import { cn } from "@/lib/utils";

type Tone = "accent" | "warning" | "error" | "info";

type Props = {
  value: number;
  label?: string;
  detail?: string;
  tone?: Tone;
  className?: string;
  showValue?: boolean;
};

export default function SignalMeter({
  value,
  label,
  detail,
  tone = "accent",
  className,
  showValue = true,
}: Props) {
  const reducedMotion = useReducedMotion();
  const percentage = Math.max(0, Math.min(100, Math.round(value)));

  return (
    <div className={cn("sf-meter", className)}>
      {(label || detail || showValue) && (
        <div className="sf-meter__meta">
          <span>{label}</span>
          <span>{detail ?? (showValue ? `${percentage}%` : "")}</span>
        </div>
      )}
      <div
        className="sf-meter__track"
        role="progressbar"
        aria-label={label ?? "Progress"}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percentage}
      >
        <motion.span
          className={cn("sf-meter__fill", `sf-meter__fill--${tone}`)}
          initial={reducedMotion ? false : { scaleX: 0 }}
          animate={{ scaleX: percentage / 100 }}
          transition={reducedMotion ? { duration: 0 } : { duration: 0.55, ease: [0.22, 1, 0.36, 1] }}
        />
      </div>
    </div>
  );
}
