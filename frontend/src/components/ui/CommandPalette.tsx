"use client";

import {
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
} from "react";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { Command, Search } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  SIGNAL_EASE,
  SIGNAL_MOTION,
  signalInstantTransition,
} from "@/components/ui/Animations";

export type CommandPaletteAction = {
  id: string;
  label: string;
  onSelect: () => void;
  description?: string;
  keywords?: readonly string[];
  icon?: ReactNode;
  shortcut?: string;
  group?: string;
  disabled?: boolean;
};

export type CommandPaletteProps = {
  actions: readonly CommandPaletteAction[];
  open?: boolean;
  defaultOpen?: boolean;
  onOpenChange?: (open: boolean) => void;
  title?: string;
  description?: string;
  emptyMessage?: string;
  hotkeyEnabled?: boolean;
  className?: string;
};

const focusableSelector = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "[tabindex]:not([tabindex='-1'])",
].join(",");

export default function CommandPalette({
  actions,
  open,
  defaultOpen = false,
  onOpenChange,
  title = "Command palette",
  description = "Search available workspace actions.",
  emptyMessage = "No matching commands.",
  hotkeyEnabled = true,
  className,
}: CommandPaletteProps) {
  const [uncontrolledOpen, setUncontrolledOpen] = useState(defaultOpen);
  const [query, setQuery] = useState("");
  const [activeIndexState, setActiveIndexState] = useState(0);
  const dialogRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const lastFocusedRef = useRef<HTMLElement | null>(null);
  const wasOpenRef = useRef(false);
  const dialogId = useId();
  const isControlled = open !== undefined;
  const isOpen = open ?? uncontrolledOpen;
  const shouldReduceMotion = useReducedMotion() === true;
  const titleId = `${dialogId}-title`;
  const descriptionId = `${dialogId}-description`;
  const commandsId = `${dialogId}-commands`;

  const requestOpenChange = useCallback(
    (nextOpen: boolean) => {
      if (!isControlled) setUncontrolledOpen(nextOpen);
      onOpenChange?.(nextOpen);
    },
    [isControlled, onOpenChange]
  );

  const close = useCallback(() => {
    requestOpenChange(false);
    setQuery("");
    setActiveIndexState(0);
  }, [requestOpenChange]);

  const openPalette = useCallback(() => {
    requestOpenChange(true);
  }, [requestOpenChange]);

  const filteredActions = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase();
    if (!normalizedQuery) return actions;

    return actions.filter((action) =>
      [
        action.label,
        action.description,
        action.group,
        ...(action.keywords ?? []),
      ]
        .filter(Boolean)
        .join(" ")
        .toLocaleLowerCase()
        .includes(normalizedQuery)
    );
  }, [actions, query]);

  const activeIndex = filteredActions.length
    ? Math.min(activeIndexState, filteredActions.length - 1)
    : -1;

  const runAction = useCallback(
    (action: CommandPaletteAction) => {
      if (action.disabled) return;
      close();
      action.onSelect();
    },
    [close]
  );

  useEffect(() => {
    if (!hotkeyEnabled) return;

    const handleGlobalKeyDown = (event: KeyboardEvent) => {
      if (event.isComposing) return;

      const isPaletteShortcut =
        (event.metaKey || event.ctrlKey) &&
        !event.altKey &&
        event.key.toLocaleLowerCase() === "k";

      if (isPaletteShortcut) {
        event.preventDefault();
        if (isOpen) close();
        else openPalette();
        return;
      }

      if (event.key === "Escape" && isOpen) {
        event.preventDefault();
        close();
      }
    };

    document.addEventListener("keydown", handleGlobalKeyDown);
    return () => document.removeEventListener("keydown", handleGlobalKeyDown);
  }, [close, hotkeyEnabled, isOpen, openPalette]);

  useEffect(() => {
    if (isOpen && !wasOpenRef.current) {
      lastFocusedRef.current = document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
      wasOpenRef.current = true;
      const frame = window.requestAnimationFrame(() => inputRef.current?.focus());
      return () => window.cancelAnimationFrame(frame);
    }

    if (!isOpen && wasOpenRef.current) {
      wasOpenRef.current = false;
      lastFocusedRef.current?.focus();
    }
  }, [isOpen]);

  const trapFocus = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key !== "Tab") return;
    const dialog = dialogRef.current;
    if (!dialog) return;

    const focusableElements = Array.from(
      dialog.querySelectorAll<HTMLElement>(focusableSelector)
    ).filter((element) => element.getAttribute("aria-hidden") !== "true");

    if (focusableElements.length === 0) {
      event.preventDefault();
      dialog.focus();
      return;
    }

    const first = focusableElements[0];
    const last = focusableElements[focusableElements.length - 1];
    if (!first || !last) return;
    const activeElement = document.activeElement;

    if (
      event.shiftKey &&
      (activeElement === first || !dialog.contains(activeElement))
    ) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  const handleKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Tab") {
      trapFocus(event);
      return;
    }

    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      close();
      return;
    }

    if (activeIndex < 0) return;

    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveIndexState((current) => (current + 1) % filteredActions.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveIndexState(
        (current) => (current - 1 + filteredActions.length) % filteredActions.length
      );
    } else if (event.key === "Enter") {
      event.preventDefault();
      const action = filteredActions[activeIndex];
      if (action) runAction(action);
    }
  };

  return (
    <AnimatePresence initial={false}>
      {isOpen && (
        <motion.div
          className="fixed inset-0 z-[100] flex items-start justify-center overflow-y-auto bg-background/80 px-4 py-[max(1rem,10vh)]"
          initial={shouldReduceMotion ? false : { opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={shouldReduceMotion ? undefined : { opacity: 0 }}
          transition={
            shouldReduceMotion
              ? signalInstantTransition
              : { duration: SIGNAL_MOTION.fast, ease: SIGNAL_EASE }
          }
        >
          <button
            type="button"
            aria-label="Close command palette"
            className="absolute inset-0 h-full w-full cursor-default"
            onClick={close}
            tabIndex={-1}
          />
          <motion.div
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            aria-describedby={descriptionId}
            aria-keyshortcuts="Control+K Meta+K"
            tabIndex={-1}
            onKeyDown={handleKeyDown}
            className={cn(
              "relative w-full max-w-xl overflow-hidden border border-border bg-card text-foreground",
              className
            )}
            initial={
              shouldReduceMotion
                ? false
                : { opacity: 0, y: SIGNAL_MOTION.travel }
            }
            animate={{ opacity: 1, y: 0 }}
            exit={
              shouldReduceMotion
                ? undefined
                : { opacity: 0, y: SIGNAL_MOTION.travel }
            }
            transition={
              shouldReduceMotion
                ? signalInstantTransition
                : { duration: SIGNAL_MOTION.standard, ease: SIGNAL_EASE }
            }
          >
            <header className="border-b border-border px-4 py-3 sm:px-5">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="sf-meta text-accent">Quick actions</p>
                  <h2 id={titleId} className="mt-1 text-base font-semibold">
                    {title}
                  </h2>
                </div>
                <kbd className="hidden items-center gap-1 border border-border bg-background-secondary px-2 py-1 font-mono text-[0.65rem] text-foreground-secondary sm:inline-flex">
                  <Command className="h-3 w-3" aria-hidden="true" />K
                </kbd>
              </div>
              <p id={descriptionId} className="sr-only">
                {description}
              </p>
            </header>

            <div className="border-b border-border px-4 py-3 sm:px-5">
              <label className="sr-only" htmlFor={`${dialogId}-search`}>
                Search commands
              </label>
              <div className="flex items-center gap-3 border border-input-border bg-input-bg px-3 py-2 focus-within:border-input-focus focus-within:ring-2 focus-within:ring-accent/20">
                <Search className="h-4 w-4 shrink-0 text-foreground-secondary" aria-hidden="true" />
                <input
                  ref={inputRef}
                  id={`${dialogId}-search`}
                  type="text"
                  value={query}
                  onChange={(event) => {
                    setQuery(event.target.value);
                    setActiveIndexState(0);
                  }}
                  placeholder="Search actions"
                  className="min-w-0 flex-1 border-0 bg-transparent p-0 text-sm text-foreground placeholder:text-placeholder focus:outline-none"
                />
                <span className="sf-meta hidden text-foreground-tertiary sm:inline">
                  {filteredActions.length} available
                </span>
              </div>
            </div>

            <p className="sr-only" aria-live="polite">
              {filteredActions.length === 1
                ? "1 command available"
                : `${filteredActions.length} commands available`}
            </p>

            <div
              id={commandsId}
              role="menu"
              aria-label="Available commands"
              className="max-h-[min(52vh,28rem)] overflow-y-auto p-2"
            >
              {filteredActions.length === 0 ? (
                <p className="px-3 py-8 text-center text-sm text-foreground-secondary">
                  {emptyMessage}
                </p>
              ) : (
                filteredActions.map((action, index) => {
                  const showGroup =
                    action.group &&
                    (index === 0 ||
                      filteredActions[index - 1]?.group !== action.group);

                  return (
                    <div key={action.id} role="none">
                      {showGroup && (
                        <p role="presentation" className="sf-meta px-3 pb-1 pt-2 text-foreground-tertiary">
                          {action.group}
                        </p>
                      )}
                      <button
                        id={`${commandsId}-command-${index}`}
                        type="button"
                        role="menuitem"
                        aria-label={action.group ? `${action.group}: ${action.label}` : undefined}
                        aria-disabled={action.disabled || undefined}
                        disabled={action.disabled}
                        onMouseEnter={() => setActiveIndexState(index)}
                        onClick={() => runAction(action)}
                        className={cn(
                          "flex w-full items-center gap-3 border border-transparent px-3 py-3 text-left transition-colors duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-accent",
                          index === activeIndex
                            ? "border-accent/30 bg-accent-light text-foreground"
                            : "text-foreground-secondary hover:border-border hover:bg-background-secondary hover:text-foreground",
                          action.disabled && "cursor-not-allowed opacity-45"
                        )}
                      >
                        {action.icon && (
                          <span className="grid h-7 w-7 shrink-0 place-items-center border border-border bg-background-secondary text-accent">
                            {action.icon}
                          </span>
                        )}
                        <span className="min-w-0 flex-1">
                          <span className="block text-sm font-medium">{action.label}</span>
                          {action.description && (
                            <span className="mt-0.5 block truncate text-xs text-foreground-secondary">
                              {action.description}
                            </span>
                          )}
                        </span>
                        {action.shortcut && (
                          <kbd className="sf-meta shrink-0 border border-border bg-background-secondary px-1.5 py-0.5 text-foreground-secondary">
                            {action.shortcut}
                          </kbd>
                        )}
                      </button>
                    </div>
                  );
                })
              )}
            </div>

            <footer className="flex items-center justify-between border-t border-border px-4 py-2.5 text-[0.68rem] text-foreground-tertiary sm:px-5">
              <span className="font-mono">Up/down navigate - Enter select</span>
              <button
                type="button"
                className="font-mono transition-colors hover:text-foreground focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                onClick={close}
              >
                Esc close
              </button>
            </footer>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
