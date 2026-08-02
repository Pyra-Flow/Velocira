"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, KeyRound, Mail } from "lucide-react";
import { motion } from "framer-motion";
import { useLocale } from "@/providers/LocaleProvider";
import { authApi } from "@/lib/api";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import AuthPageFrame from "@/components/auth/AuthPageFrame";
import { PageTransition } from "@/components/ui/Animations";

export default function ForgotPasswordPage() {
  const { t } = useLocale();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [sent, setSent] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    if (!email) {
      setError(t("errors.required"));
      return;
    }
    setLoading(true);
    const response = await authApi.forgotPassword(email);
    setLoading(false);
    if (!response.success) {
      setError(response.message);
      return;
    }
    setSent(true);
    setTimeout(() => router.push(`/reset-password?email=${encodeURIComponent(email)}`), 2000);
  };

  return (
    <PageTransition>
      <AuthPageFrame
        icon={KeyRound}
        eyebrow="Recovery flow"
        title={t("auth.forgot.title")}
        description={t("auth.forgot.subtitle")}
        footer={<Link href="/login" className="auth-page__back"><ArrowLeft className="h-4 w-4" />{t("auth.forgot.back")}</Link>}
      >
        {error && <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} role="alert" className="auth-page__notice auth-page__notice--error">{error}</motion.div>}
        {sent ? (
          <motion.div initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} className="auth-page__success" role="status">
            <Mail aria-hidden="true" />
            <p>{t("common.success")}</p>
            <span>Check {email}. We&apos;ll take you to the reset screen next.</span>
          </motion.div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <Input label={t("auth.forgot.email")} type="email" icon={<Mail className="h-4 w-4" />} placeholder="you@example.com" value={email} onChange={(event) => setEmail(event.target.value)} required autoComplete="email" />
            <Button type="submit" className="w-full" size="lg" loading={loading}>{t("auth.forgot.submit")}</Button>
          </form>
        )}
      </AuthPageFrame>
    </PageTransition>
  );
}
