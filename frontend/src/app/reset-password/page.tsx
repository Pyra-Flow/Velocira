"use client";

import { Suspense, useState, type FormEvent } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Link from "next/link";
import { Lock, KeyRound, Zap } from "lucide-react";
import { motion } from "framer-motion";
import { useLocale } from "@/providers/LocaleProvider";
import { authApi } from "@/lib/api";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import { PageTransition } from "@/components/ui/Animations";

function ResetPasswordForm() {
  const { t } = useLocale();
  const router = useRouter();
  const searchParams = useSearchParams();
  const email = searchParams.get("email") || "";

  const [otp, setOtp] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");

    if (!otp || !newPassword) {
      setError(t("errors.required"));
      return;
    }

    const passwordRegex =
      /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
    if (!passwordRegex.test(newPassword)) {
      setError(t("errors.passwordWeak"));
      return;
    }

    setLoading(true);
    const res = await authApi.resetPassword({ email, otp, newPassword });
    setLoading(false);

    if (res.success) {
      setSuccess(true);
      setTimeout(() => router.push("/login"), 2000);
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
                {t("auth.reset.title")}
              </h1>
              <p className="text-sm text-foreground-secondary mt-1">
                {t("auth.reset.subtitle")}
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

            {success ? (
              <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                className="text-center p-6"
              >
                <div className="h-16 w-16 rounded-full bg-success/10 flex items-center justify-center mx-auto mb-4">
                  <Lock className="h-8 w-8 text-success" />
                </div>
                <p className="text-foreground font-medium">
                  {t("common.success")}
                </p>
              </motion.div>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-4">
                <Input
                  label={t("auth.reset.otp")}
                  type="text"
                  icon={<KeyRound className="h-4 w-4" />}
                  placeholder="123456"
                  value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))}
                  required
                />

                <div>
                  <Input
                    label={t("auth.reset.password")}
                    type="password"
                    icon={<Lock className="h-4 w-4" />}
                    placeholder="••••••••"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    required
                    autoComplete="new-password"
                  />
                  <p className="text-xs text-foreground-secondary mt-1">
                    {t("auth.register.passwordHint")}
                  </p>
                </div>

                <Button
                  type="submit"
                  className="w-full"
                  size="lg"
                  loading={loading}
                >
                  {t("auth.reset.submit")}
                </Button>
              </form>
            )}
          </div>
        </motion.div>
      </div>
    </PageTransition>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center">
          <div className="h-8 w-8 border-2 border-primary border-t-transparent rounded-full animate-spin" />
        </div>
      }
    >
      <ResetPasswordForm />
    </Suspense>
  );
}
