"use client";

import { useEffect, useId, useRef, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { AlertTriangle } from "lucide-react";
import Button from "@/components/ui/Button";
import { SIGNAL_EASE, SIGNAL_MOTION, signalInstantTransition } from "@/components/ui/Animations";

type Props = {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  cancelLabel?: string;
  tone?: "danger" | "warning";
  isSubmitting?: boolean;
  onConfirm: () => void | Promise<void>;
  onOpenChange: (open: boolean) => void;
};

const focusableSelector = "a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex='-1'])";

export default function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel = "Cancel",
  tone = "danger",
  isSubmitting = false,
  onConfirm,
  onOpenChange,
}: Props) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const lastFocusedRef = useRef<HTMLElement | null>(null);
  const dialogId = useId();
  const titleId = `${dialogId}-title`;
  const descriptionId = `${dialogId}-description`;
  const shouldReduceMotion = useReducedMotion() === true;

  useEffect(() => {
    if (!open) return;
    lastFocusedRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const frame = window.requestAnimationFrame(() => {
      dialogRef.current?.querySelector<HTMLElement>("[data-dialog-autofocus], button:not([disabled])")?.focus();
    });
    return () => {
      window.cancelAnimationFrame(frame);
      lastFocusedRef.current?.focus();
    };
  }, [open]);

  const close = () => {
    if (!isSubmitting) onOpenChange(false);
  };

  const handleKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Escape") {
      event.preventDefault();
      close();
      return;
    }
    if (event.key !== "Tab") return;
    const dialog = dialogRef.current;
    if (!dialog) return;
    const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(focusableSelector));
    if (focusable.length === 0) {
      event.preventDefault();
      dialog.focus();
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (!first || !last) return;
    if (event.shiftKey && (document.activeElement === first || !dialog.contains(document.activeElement))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  return (
    <AnimatePresence initial={false}>
      {open && (
        <motion.div
          className="fixed inset-0 z-[100] flex items-center justify-center bg-background/80 p-4"
          initial={shouldReduceMotion ? false : { opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={shouldReduceMotion ? undefined : { opacity: 0 }}
          transition={shouldReduceMotion ? signalInstantTransition : { duration: SIGNAL_MOTION.fast, ease: SIGNAL_EASE }}
        >
          <button type="button" className="absolute inset-0 cursor-default" tabIndex={-1} aria-label={`Close ${title}`} onClick={close} />
          <motion.div
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            aria-describedby={descriptionId}
            tabIndex={-1}
            onKeyDown={handleKeyDown}
            className="relative w-full max-w-md border border-border bg-card p-5 text-foreground sm:p-6"
            initial={shouldReduceMotion ? false : { opacity: 0, y: SIGNAL_MOTION.travel }}
            animate={{ opacity: 1, y: 0 }}
            exit={shouldReduceMotion ? undefined : { opacity: 0, y: SIGNAL_MOTION.travel }}
            transition={shouldReduceMotion ? signalInstantTransition : { duration: SIGNAL_MOTION.standard, ease: SIGNAL_EASE }}
          >
            <div className="flex gap-3">
              <span className={`grid h-10 w-10 shrink-0 place-items-center border ${tone === "danger" ? "border-error/30 bg-error/10 text-error" : "border-warning/30 bg-warning/10 text-warning"}`}>
                <AlertTriangle className="h-5 w-5" aria-hidden="true" />
              </span>
              <div>
                <h2 id={titleId} className="text-lg font-semibold">{title}</h2>
                <p id={descriptionId} className="mt-1 text-sm leading-6 text-foreground-secondary">{description}</p>
              </div>
            </div>
            <div className="mt-6 flex flex-wrap justify-end gap-2">
              <Button data-dialog-autofocus type="button" variant="ghost" onClick={close} disabled={isSubmitting}>{cancelLabel}</Button>
              <Button type="button" variant={tone === "danger" ? "danger" : "warning"} loading={isSubmitting} onClick={() => void onConfirm()}>{confirmLabel}</Button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
