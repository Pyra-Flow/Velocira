"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Mail, ArrowLeft, Zap, KeyRound } from "lucide-react";
import { motion } from "framer-motion";
import { useLocale } from "@/providers/LocaleProvider";
import { authApi } from "@/lib/api";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import { PageTransition } from "@/components/ui/Animations";

export default function ForgotPasswordPage() {
  const { t } = useLocale();
  const router = useRouter();

  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [sent, setSent] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");

    if (!email) {
      setError(t("errors.required"));
      return;
    }

    setLoading(true);
    const res = await authApi.forgotPassword(email);
    setLoading(false);

    if (res.success) {
      setSent(true);
      setTimeout(
        () => router.push(`/reset-password?email=${encodeURIComponent(email)}`),
        2000
      );
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
          <div className="glass rounded-2xl border border-border p-8 shadow-xl">
            <div className="text-center mb-8">
              <Link href="/" className="inline-flex items-center gap-2 mb-6">
                <Zap className="h-8 w-8 text-primary" />
                <span className="text-2xl font-bold font-display gradient-text">
                  Velocira
                </span>
              </Link>
              <div className="h-16 w-16 rounded-2xl bg-primary/10 flex items-center justify-center mx-auto mb-4">
                <KeyRound className="h-8 w-8 text-primary" />
              </div>
              <h1 className="text-2xl font-bold font-display text-foreground">
                {t("auth.forgot.title")}
              </h1>
              <p className="text-sm text-foreground-secondary mt-1">
                {t("auth.forgot.subtitle")}
              </p>
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

            {sent ? (
              <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                className="text-center p-6"
              >
                <div className="h-16 w-16 rounded-full bg-success/10 flex items-center justify-center mx-auto mb-4">
                  <Mail className="h-8 w-8 text-success" />
                </div>
                <p className="text-foreground font-medium">
                  {t("common.success")}
                </p>
                <p className="text-sm text-foreground-secondary mt-2">
                  {t("auth.forgot.subtitle")}
                </p>
              </motion.div>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-4">
                <Input
                  label={t("auth.forgot.email")}
                  type="email"
                  icon={<Mail className="h-4 w-4" />}
                  placeholder="you@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoComplete="email"
                />

                <Button
                  type="submit"
                  className="w-full"
                  size="lg"
                  loading={loading}
                >
                  {t("auth.forgot.submit")}
                </Button>
              </form>
            )}

            <div className="mt-6 text-center">
              <Link
                href="/login"
                className="inline-flex items-center gap-1 text-sm text-foreground-secondary hover:text-primary transition-colors"
              >
                <ArrowLeft className="h-4 w-4" />
                {t("auth.forgot.back")}
              </Link>
            </div>
          </div>
        </motion.div>
      </div>
    </PageTransition>
  );
}
