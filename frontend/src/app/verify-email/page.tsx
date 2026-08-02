"use client";

import { Suspense, useState, useRef, useEffect, type FormEvent, type KeyboardEvent } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { ShieldCheck } from "lucide-react";
import { motion } from "framer-motion";
import { useLocale } from "@/providers/LocaleProvider";
import { authApi } from "@/lib/api";
import Button from "@/components/ui/Button";
import AuthPageFrame from "@/components/auth/AuthPageFrame";
import { PageTransition } from "@/components/ui/Animations";

function VerifyEmailForm() {
  const { t } = useLocale();
  const router = useRouter();
  const email = useSearchParams().get("email") || "";
  const [otp, setOtp] = useState(["", "", "", "", "", ""]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [resendCooldown, setResendCooldown] = useState(0);
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);
  const cooldownRef = useRef<ReturnType<typeof setInterval>>(undefined);

  useEffect(() => () => { if (cooldownRef.current) clearInterval(cooldownRef.current); }, []);

  const handleChange = (index: number, value: string) => {
    if (!/^\d*$/.test(value)) return;
    const next = [...otp];
    next[index] = value.slice(-1);
    setOtp(next);
    if (value && index < 5) inputRefs.current[index + 1]?.focus();
  };
  const handleKeyDown = (index: number, event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Backspace" && !otp[index] && index > 0) inputRefs.current[index - 1]?.focus();
  };
  const handlePaste = (event: React.ClipboardEvent) => {
    event.preventDefault();
    const code = event.clipboardData.getData("text").replace(/\D/g, "").slice(0, 6);
    setOtp([...code.split(""), "", "", "", "", ""].slice(0, 6));
    if (code) inputRefs.current[Math.min(code.length, 5)]?.focus();
  };
  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    setSuccess("");
    const code = otp.join("");
    if (code.length !== 6) {
      setError(t("errors.required"));
      return;
    }
    setLoading(true);
    const response = await authApi.verifyEmail({ email, otp: code });
    setLoading(false);
    if (!response.success) {
      setError(response.message);
      return;
    }
    setSuccess(t("common.success"));
    setTimeout(() => router.push("/login"), 1500);
  };
  const handleResend = async () => {
    if (resendCooldown > 0) return;
    const response = await authApi.resendOtp(email);
    if (!response.success) {
      setError(response.message);
      return;
    }
    setResendCooldown(60);
    if (cooldownRef.current) clearInterval(cooldownRef.current);
    cooldownRef.current = setInterval(() => setResendCooldown((value) => {
      if (value <= 1) {
        clearInterval(cooldownRef.current);
        return 0;
      }
      return value - 1;
    }), 1000);
  };

  return (
    <PageTransition>
      <AuthPageFrame icon={ShieldCheck} eyebrow="Email verification" title={t("auth.verify.title")} description={t("auth.verify.subtitle")}>
        {email && <p className="auth-page__email">{email}</p>}
        {error && <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} role="alert" className="auth-page__notice auth-page__notice--error">{error}</motion.div>}
        {success && <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} role="status" className="auth-page__notice auth-page__notice--success">{success}</motion.div>}
        <form onSubmit={handleSubmit}>
          <div className="auth-page__otp" onPaste={handlePaste}>
            {otp.map((digit, index) => <input key={index} ref={(element) => { inputRefs.current[index] = element; }} type="text" inputMode="numeric" maxLength={1} value={digit} onChange={(event) => handleChange(index, event.target.value)} onKeyDown={(event) => handleKeyDown(index, event)} aria-label={`Verification digit ${index + 1}`} autoComplete={index === 0 ? "one-time-code" : "off"} />)}
          </div>
          <Button type="submit" className="w-full" size="lg" loading={loading}>{t("auth.verify.submit")}</Button>
        </form>
        <button type="button" onClick={handleResend} disabled={resendCooldown > 0} className="auth-page__resend">
          {resendCooldown > 0 ? t("auth.verify.resendIn", { seconds: String(resendCooldown) }) : t("auth.verify.resend")}
        </button>
      </AuthPageFrame>
    </PageTransition>
  );
}

export default function VerifyEmailPage() {
  return <Suspense fallback={<div className="auth-page"><div className="workspace-skeleton h-64 w-full max-w-md" /></div>}><VerifyEmailForm /></Suspense>;
}
