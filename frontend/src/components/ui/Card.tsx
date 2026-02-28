"use client";

import { cn } from "@/lib/utils";
import { type ReactNode } from "react";

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  className?: string;
  children: ReactNode;
  hover?: boolean;
  glow?: boolean;
}

export default function Card({
  className,
  children,
  hover = false,
  glow = false,
  ...rest
}: CardProps) {
  return (
    <div
      className={cn(
        "rounded-2xl border border-border bg-card p-6",
        "shadow-sm",
        "transition-all duration-300",
        hover && "hover:border-border-hover hover:bg-card-hover hover:-translate-y-0.5 hover:shadow-md",
        glow && "border-primary/20",
        className
      )}
      {...rest}
    >
      {children}
    </div>
  );
}
