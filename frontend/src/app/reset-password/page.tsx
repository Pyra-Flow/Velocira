"use client";

import { Suspense, useState, type FormEvent } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { KeyRound, Lock } from "lucide-react";
import { motion } from "framer-motion";
import { useLocale } from "@/providers/LocaleProvider";
import { authApi } from "@/lib/api";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import AuthPageFrame from "@/components/auth/AuthPageFrame";
import { PageTransition } from "@/components/ui/Animations";

function ResetPasswordForm() {
  const { t } = useLocale();
  const router = useRouter();
  const email = useSearchParams().get("email") || "";
  const [otp, setOtp] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    if (!otp || !newPassword) {
      setError(t("errors.required"));
      return;
    }
    const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
    if (!passwordRegex.test(newPassword)) {
      setError(t("errors.passwordWeak"));
      return;
    }
    setLoading(true);
    const response = await authApi.resetPassword({ email, otp, newPassword });
    setLoading(false);
    if (!response.success) {
      setError(response.message);
      return;
    }
    setSuccess(true);
    setTimeout(() => router.push("/login"), 2000);
  };

  return (
    <PageTransition>
      <AuthPageFrame icon={KeyRound} eyebrow="Secure reset" title={t("auth.reset.title")} description={t("auth.reset.subtitle")}>
        {error && <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} role="alert" className="auth-page__notice auth-page__notice--error">{error}</motion.div>}
        {success ? (
          <motion.div initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} className="auth-page__success" role="status"><Lock aria-hidden="true" /><p>{t("common.success")}</p><span>Your password has changed. Redirecting to sign in…</span></motion.div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <Input label={t("auth.reset.otp")} type="text" inputMode="numeric" icon={<KeyRound className="h-4 w-4" />} placeholder="123456" value={otp} onChange={(event) => setOtp(event.target.value.replace(/\D/g, "").slice(0, 6))} required />
            <div><Input label={t("auth.reset.password")} type="password" icon={<Lock className="h-4 w-4" />} placeholder="••••••••" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} required autoComplete="new-password" /><p className="mt-1 text-xs text-foreground-secondary">{t("auth.register.passwordHint")}</p></div>
            <Button type="submit" className="w-full" size="lg" loading={loading}>{t("auth.reset.submit")}</Button>
          </form>
        )}
      </AuthPageFrame>
    </PageTransition>
  );
}

export default function ResetPasswordPage() {
  return <Suspense fallback={<div className="auth-page"><div className="workspace-skeleton h-64 w-full max-w-md" /></div>}><ResetPasswordForm /></Suspense>;
}
