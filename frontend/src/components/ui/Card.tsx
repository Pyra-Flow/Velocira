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
        "relative overflow-hidden rounded-sm border border-border bg-card p-5",
        "transition-[border-color,background-color] duration-150",
        hover && "hover:border-accent/55 hover:bg-card-hover",
        glow && "border-accent/40",
        className
      )}
      {...rest}
    >
      {children}
    </div>
  );
}
