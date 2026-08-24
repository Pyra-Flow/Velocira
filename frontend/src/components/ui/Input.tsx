"use client";

import { forwardRef, type InputHTMLAttributes, useId, useState } from "react";
import { cn } from "@/lib/utils";
import { Eye, EyeOff } from "lucide-react";

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  icon?: React.ReactNode;
}

const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, error, icon, type, id, "aria-describedby": ariaDescribedBy, "aria-invalid": ariaInvalid, ...props }, ref) => {
    const [showPassword, setShowPassword] = useState(false);
    const isPassword = type === "password";
    const generatedId = useId();
    const inputId = id ?? generatedId;
    const errorId = `${inputId}-error`;
    const describedBy = [ariaDescribedBy, error ? errorId : undefined].filter(Boolean).join(" ") || undefined;

    return (
      <div className="w-full space-y-1.5">
        {label && (
          <label htmlFor={inputId} className="block font-mono text-[10px] font-medium uppercase tracking-[0.1em] text-foreground-secondary">
            {label}
          </label>
        )}
        <div className="relative">
          {icon && (
            <div className="absolute inset-y-0 start-0 flex items-center ps-3 pointer-events-none text-placeholder">
              {icon}
            </div>
          )}
          <input
            ref={ref}
            id={inputId}
            type={isPassword && showPassword ? "text" : type}
            className={cn(
              "min-h-11 w-full rounded-sm border border-input-border bg-input-bg px-3.5 py-2.5 text-sm text-foreground",
              "placeholder:text-placeholder transition-colors duration-150",
              "focus:outline-none focus:border-input-focus focus:ring-2 focus:ring-accent/20",
              "disabled:opacity-50 disabled:cursor-not-allowed",
              icon && "ps-10",
              isPassword && "pe-10",
              error && "border-error focus:border-error focus:ring-error/20",
              className
            )}
            {...props}
            aria-describedby={describedBy}
            aria-invalid={error ? true : ariaInvalid}
          />
          {isPassword && (
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute inset-y-0 end-0 flex items-center pe-3 text-placeholder hover:text-foreground transition-colors"
              aria-label={showPassword ? "Hide password" : "Show password"}
            >
              {showPassword ? (
                <EyeOff className="h-4 w-4" />
              ) : (
                <Eye className="h-4 w-4" />
              )}
            </button>
          )}
        </div>
        {error && (
          <p id={errorId} className="mt-1 font-mono text-xs text-error" role="alert">{error}</p>
        )}
      </div>
    );
  }
);

Input.displayName = "Input";
export default Input;
