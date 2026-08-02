"use client";

import { cloneElement, isValidElement, useId, useState, type ReactElement, type ReactNode } from "react";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { SIGNAL_EASE, SIGNAL_MOTION, signalInstantTransition } from "@/components/ui/Animations";

type TriggerProps = {
  "aria-describedby"?: string;
  onFocus?: (event: React.FocusEvent<HTMLElement>) => void;
  onBlur?: (event: React.FocusEvent<HTMLElement>) => void;
  onKeyDown?: (event: React.KeyboardEvent<HTMLElement>) => void;
};

type Props = {
  label: string;
  children: ReactElement<TriggerProps>;
  side?: "top" | "bottom";
};

export default function Tooltip({ label, children, side = "bottom" }: Props) {
  const [open, setOpen] = useState(false);
  const id = useId();
  const reducedMotion = useReducedMotion() === true;

  if (!isValidElement<TriggerProps>(children)) return children as ReactNode;

  const trigger = children as ReactElement<TriggerProps>;
  const existingDescription = trigger.props["aria-describedby"];
  const describedBy = [existingDescription, open ? id : undefined]
    .filter(Boolean)
    .join(" ") || undefined;

  const child = cloneElement(trigger, {
    "aria-describedby": describedBy,
    onFocus: (event: React.FocusEvent<HTMLElement>) => {
      trigger.props.onFocus?.(event);
      setOpen(true);
    },
    onBlur: (event: React.FocusEvent<HTMLElement>) => {
      trigger.props.onBlur?.(event);
      setOpen(false);
    },
    onKeyDown: (event: React.KeyboardEvent<HTMLElement>) => {
      trigger.props.onKeyDown?.(event);
      if (event.key === "Escape") setOpen(false);
    },
  });

  return (
    <span className="sf-tooltip" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}>
      {child}
      <AnimatePresence>
        {open && <motion.span id={id} role="tooltip" className={`sf-tooltip__bubble sf-tooltip__bubble--${side}`} initial={reducedMotion ? false : { opacity: 0, y: side === "top" ? 4 : -4 }} animate={{ opacity: 1, y: 0 }} exit={reducedMotion ? undefined : { opacity: 0, y: side === "top" ? 4 : -4 }} transition={reducedMotion ? signalInstantTransition : { duration: SIGNAL_MOTION.fast, ease: SIGNAL_EASE }}>{label}</motion.span>}
      </AnimatePresence>
    </span>
  );
}
