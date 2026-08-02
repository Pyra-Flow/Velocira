"use client";

import {
  motion,
  useReducedMotion,
  type HTMLMotionProps,
  type Transition,
  type Variants,
} from "framer-motion";
import { type ReactNode } from "react";

export type SignalMotionDirection = "up" | "down" | "left" | "right";

/**
 * Shared movement limits for the Signal Forge interface. These are deliberately
 * short and spatially restrained so state changes remain informative rather
 * than decorative.
 */
export const SIGNAL_EASE: [number, number, number, number] = [0.22, 1, 0.36, 1];

export const SIGNAL_MOTION = {
  fast: 0.14,
  standard: 0.2,
  reveal: 0.28,
  stagger: 0.06,
  travel: 10,
  scale: 0.98,
} as const;

export const signalInstantTransition: Transition = { duration: 0 };

export const signalStaggerItemVariants: Variants = {
  hidden: { opacity: 0, y: SIGNAL_MOTION.travel },
  visible: {
    opacity: 1,
    y: 0,
    transition: {
      duration: SIGNAL_MOTION.reveal,
      ease: SIGNAL_EASE,
    },
  },
};

function directionOffset(direction: SignalMotionDirection) {
  switch (direction) {
    case "down":
      return { x: 0, y: -SIGNAL_MOTION.travel };
    case "left":
      return { x: SIGNAL_MOTION.travel, y: 0 };
    case "right":
      return { x: -SIGNAL_MOTION.travel, y: 0 };
    case "up":
    default:
      return { x: 0, y: SIGNAL_MOTION.travel };
  }
}

export function signalRevealVariants(
  direction: SignalMotionDirection = "up"
): Variants {
  const offset = directionOffset(direction);

  return {
    hidden: { opacity: 0, ...offset },
    visible: {
      opacity: 1,
      x: 0,
      y: 0,
      transition: {
        duration: SIGNAL_MOTION.reveal,
        ease: SIGNAL_EASE,
      },
    },
  };
}

/**
 * Use this for stateful motion that cannot use the wrapper components below.
 * `useReducedMotion` is intentionally checked locally as a second line of
 * defence in addition to the app-level MotionConfig.
 */
export function useSignalMotion() {
  const shouldReduceMotion = useReducedMotion() === true;

  return {
    shouldReduceMotion,
    transition: shouldReduceMotion
      ? signalInstantTransition
      : {
          duration: SIGNAL_MOTION.standard,
          ease: SIGNAL_EASE,
        },
  };
}

type AnimatedSectionProps = Omit<
  HTMLMotionProps<"div">,
  "initial" | "animate" | "whileInView" | "variants" | "transition" | "viewport"
> & {
  children: ReactNode;
  delay?: number;
  direction?: SignalMotionDirection;
  transition?: Transition;
};

type StaticAnimatedSectionProps = Omit<AnimatedSectionProps, "direction">;

const revealViewport = { once: true, margin: "-32px", amount: 0.12 };

export function FadeIn({
  children,
  delay = 0,
  direction = "up",
  transition,
  ...props
}: AnimatedSectionProps) {
  const { shouldReduceMotion } = useSignalMotion();
  const offset = directionOffset(direction);

  return (
    <motion.div
      initial={shouldReduceMotion ? false : { opacity: 0, ...offset }}
      whileInView={
        shouldReduceMotion ? undefined : { opacity: 1, x: 0, y: 0 }
      }
      viewport={revealViewport}
      transition={
        shouldReduceMotion
          ? signalInstantTransition
          : (transition ?? {
              duration: SIGNAL_MOTION.reveal,
              delay,
              ease: SIGNAL_EASE,
            })
      }
      {...props}
    >
      {children}
    </motion.div>
  );
}

export function ScaleIn({
  children,
  delay = 0,
  transition,
  ...props
}: StaticAnimatedSectionProps) {
  const { shouldReduceMotion } = useSignalMotion();

  return (
    <motion.div
      initial={
        shouldReduceMotion
          ? false
          : { opacity: 0, scale: SIGNAL_MOTION.scale, y: SIGNAL_MOTION.travel }
      }
      whileInView={
        shouldReduceMotion ? undefined : { opacity: 1, scale: 1, y: 0 }
      }
      viewport={revealViewport}
      transition={
        shouldReduceMotion
          ? signalInstantTransition
          : (transition ?? {
              duration: SIGNAL_MOTION.standard,
              delay,
              ease: SIGNAL_EASE,
            })
      }
      {...props}
    >
      {children}
    </motion.div>
  );
}

export function StaggerContainer({
  children,
  delay = 0,
  transition,
  ...props
}: StaticAnimatedSectionProps) {
  const { shouldReduceMotion } = useSignalMotion();

  return (
    <motion.div
      initial={shouldReduceMotion ? false : "hidden"}
      whileInView={shouldReduceMotion ? undefined : "visible"}
      viewport={revealViewport}
      transition={
        shouldReduceMotion
          ? signalInstantTransition
          : (transition ?? {
              staggerChildren: SIGNAL_MOTION.stagger,
              delayChildren: delay,
            })
      }
      {...props}
    >
      {children}
    </motion.div>
  );
}

export function StaggerItem({
  children,
  transition,
  ...props
}: Omit<AnimatedSectionProps, "delay" | "direction">) {
  const { shouldReduceMotion } = useSignalMotion();

  return (
    <motion.div
      variants={
        shouldReduceMotion
          ? {
              hidden: { opacity: 1, x: 0, y: 0 },
              visible: { opacity: 1, x: 0, y: 0 },
            }
          : signalStaggerItemVariants
      }
      transition={shouldReduceMotion ? signalInstantTransition : transition}
      {...props}
    >
      {children}
    </motion.div>
  );
}

type PageTransitionProps = Omit<
  HTMLMotionProps<"div">,
  "initial" | "animate" | "whileInView" | "variants" | "transition" | "viewport"
> & {
  children: ReactNode;
  transition?: Transition;
};

export function PageTransition({
  children,
  transition,
  ...props
}: PageTransitionProps) {
  const { shouldReduceMotion } = useSignalMotion();

  return (
    <motion.div
      initial={
        shouldReduceMotion ? false : { opacity: 0, y: SIGNAL_MOTION.travel }
      }
      animate={{ opacity: 1, y: 0 }}
      transition={
        shouldReduceMotion
          ? signalInstantTransition
          : (transition ?? {
              duration: SIGNAL_MOTION.standard,
              ease: SIGNAL_EASE,
            })
      }
      {...props}
    >
      {children}
    </motion.div>
  );
}
