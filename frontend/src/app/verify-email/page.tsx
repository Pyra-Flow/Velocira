"use client";

import { Suspense, useState, useRef, useEffect, type FormEvent, type KeyboardEvent } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Link from "next/link";
import { Zap, ShieldCheck } from "lucide-react";
import { motion } from "framer-motion";
import { useLocale } from "@/providers/LocaleProvider";
import { authApi } from "@/lib/api";
import Button from "@/components/ui/Button";
import { PageTransition } from "@/components/ui/Animations";

function VerifyEmailForm() {
  const { t } = useLocale();
  const router = useRouter();
  const searchParams = useSearchParams();
  const email = searchParams.get("email") || "";

  const [otp, setOtp] = useState(["", "", "", "", "", ""]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [resendCooldown, setResendCooldown] = useState(0);
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);
  const cooldownRef = useRef<ReturnType<typeof setInterval>>(undefined);

  useEffect(() => {
    return () => {
      if (cooldownRef.current) clearInterval(cooldownRef.current);
    };
  }, []);

  const handleChange = (index: number, value: string) => {
    if (!/^\d*$/.test(value)) return;
    const newOtp = [...otp];
    newOtp[index] = value.slice(-1);
    setOtp(newOtp);

    if (value && index < 5) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (index: number, e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Backspace" && !otp[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  };

  const handlePaste = (e: React.ClipboardEvent) => {
    e.preventDefault();
    const text = e.clipboardData.getData("text").replace(/\D/g, "").slice(0, 6);
    const newOtp = [...otp];
    for (let i = 0; i < text.length; i++) {
      newOtp[i] = text[i];
    }
    setOtp(newOtp);
    if (text.length > 0) {
      inputRefs.current[Math.min(text.length, 5)]?.focus();
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setSuccess("");
    const code = otp.join("");
    if (code.length !== 6) {
      setError(t("errors.required"));
      return;
    }

    setLoading(true);
    const res = await authApi.verifyEmail({ email, otp: code });
    setLoading(false);

    if (res.success) {
      setSuccess(t("common.success"));
      setTimeout(() => router.push("/login"), 1500);
    } else {
      setError(res.message);
    }
  };

  const handleResend = async () => {
    if (resendCooldown > 0) return;
    const res = await authApi.resendOtp(email);
    if (res.success) {
      setResendCooldown(60);
      if (cooldownRef.current) clearInterval(cooldownRef.current);
      cooldownRef.current = setInterval(() => {
        setResendCooldown((prev) => {
          if (prev <= 1) {
            clearInterval(cooldownRef.current);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } else {
      setError(res.message);
    }
  };

  return (
    <PageTransition>
      <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center px-4 py-12">
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          
        </div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="relative w-full max-w-md"
        >
          <div className="glass rounded-2xl border border-border p-8 shadow-xl text-center">
            <Link href="/" className="inline-flex items-center gap-2 mb-6">
              <Zap className="h-8 w-8 text-primary" />
              <span className="text-2xl font-bold font-display gradient-text">
                Velocira
              </span>
            </Link>

            <div className="mb-6">
              <div className="h-16 w-16 rounded-2xl bg-primary/10 flex items-center justify-center mx-auto mb-4">
                <ShieldCheck className="h-8 w-8 text-primary" />
              </div>
              <h1 className="text-2xl font-bold font-display text-foreground">
                {t("auth.verify.title")}
              </h1>
              <p className="text-sm text-foreground-secondary mt-1">
                {t("auth.verify.subtitle")}
              </p>
              {email && (
                <p className="text-sm text-primary font-medium mt-1">{email}</p>
              )}
            </div>

            {error && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="mb-4 p-3 rounded-xl bg-error/10 border border-error/30 text-error text-sm"
              >
                {error}
              </motion.div>
            )}

            {success && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="mb-4 p-3 rounded-xl bg-success/10 border border-success/30 text-success text-sm"
              >
                {success}
              </motion.div>
            )}

            <form onSubmit={handleSubmit}>
              <div
                className="flex items-center justify-center gap-2 mb-6"
                onPaste={handlePaste}
              >
                {otp.map((digit, i) => (
                  <input
                    key={i}
                    ref={(el) => { inputRefs.current[i] = el; }}
                    type="text"
                    inputMode="numeric"
                    maxLength={1}
                    value={digit}
                    onChange={(e) => handleChange(i, e.target.value)}
                    onKeyDown={(e) => handleKeyDown(i, e)}
                    className="w-10 h-12 sm:w-12 sm:h-14 text-center text-lg sm:text-xl font-bold rounded-xl border border-input-border bg-input-bg text-foreground focus:outline-none focus:border-input-focus focus:ring-2 focus:ring-primary/20 transition-all"
                  />
                ))}
              </div>

              <Button type="submit" className="w-full" size="lg" loading={loading}>
                {t("auth.verify.submit")}
              </Button>
            </form>

            <button
              onClick={handleResend}
              disabled={resendCooldown > 0}
              className="mt-4 text-sm text-primary hover:text-primary-hover disabled:text-foreground-secondary transition-colors"
            >
              {resendCooldown > 0
                ? t("auth.verify.resendIn", { seconds: String(resendCooldown) })
                : t("auth.verify.resend")}
            </button>
          </div>
        </motion.div>
      </div>
    </PageTransition>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center">
          <div className="h-8 w-8 border-2 border-primary border-t-transparent rounded-full animate-spin" />
        </div>
      }
    >
      <VerifyEmailForm />
    </Suspense>
  );
}
