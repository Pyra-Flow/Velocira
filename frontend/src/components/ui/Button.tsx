import { forwardRef, type ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/utils";
import { Loader2 } from "lucide-react";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "outline" | "ghost" | "danger" | "success" | "warning";
  size?: "sm" | "md" | "lg";
  loading?: boolean;
  icon?: React.ReactNode;
}

const variants = {
  primary:
    "border border-primary bg-primary text-on-primary hover:bg-primary-hover hover:border-primary-hover",
  secondary:
    "border border-accent/40 bg-accent-light text-accent hover:border-accent hover:bg-accent/15",
  outline:
    "border border-border bg-transparent text-foreground hover:border-accent/60 hover:bg-card-hover hover:text-accent",
  ghost:
    "border border-transparent text-foreground-secondary hover:border-border hover:bg-card-hover hover:text-foreground",
  danger: "border border-error/60 bg-error text-on-primary hover:bg-error/85",
  success: "border border-accent bg-accent text-on-primary hover:bg-accent/85",
  warning: "border border-warning/60 bg-warning text-on-primary hover:bg-warning/85",
};

const sizes = {
  sm: "min-h-9 px-3 py-1.5 text-xs rounded-sm",
  md: "min-h-11 px-4 py-2.5 text-sm rounded-sm",
  lg: "min-h-12 px-5 py-3 text-sm rounded-sm",
};

const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      className,
      variant = "primary",
      size = "md",
      loading = false,
      icon,
      children,
      disabled,
      ...props
    },
    ref
  ) => {
    return (
      <button
        ref={ref}
        className={cn(
          "inline-flex items-center justify-center gap-2 font-semibold tracking-[0.01em] transition-[background-color,border-color,color,transform] duration-150 focus-ring cursor-pointer active:translate-y-px",
          "disabled:cursor-not-allowed disabled:opacity-50",
          variants[variant],
          sizes[size],
          className
        )}
        disabled={disabled || loading}
        {...props}
      >
        {loading ? (
          <Loader2 className="h-4 w-4 animate-spin" />
        ) : icon ? (
          <span className="shrink-0">{icon}</span>
        ) : null}
        {children}
      </button>
    );
  }
);

Button.displayName = "Button";
export default Button;
