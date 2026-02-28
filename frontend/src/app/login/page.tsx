"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Mail, Lock, Zap } from "lucide-react";
import { motion } from "framer-motion";
import { useLocale } from "@/providers/LocaleProvider";
import { useAuthStore } from "@/store/authStore";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import GoogleAuthButton from "@/components/auth/GoogleAuthButton";
import { PageTransition } from "@/components/ui/Animations";

export default function LoginPage() {
  const { t } = useLocale();
  const { login, isLoading, error, setError } = useAuthStore();
  const router = useRouter();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!email || !password) {
      setError(t("errors.required"));
      return;
    }

    const success = await login(email, password);
    if (success) {
      router.push("/home");
    }
  };

  return (
    <PageTransition>
      <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center px-4 py-12">
        {/* Background Effects */}
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          
          
          <div className="absolute top-20 right-[12%] w-24 h-24 border border-primary/[0.06] rotate-45 hidden lg:block" />
          <div className="absolute bottom-20 left-[10%] w-16 h-16 border border-primary/[0.05] rotate-45 hidden lg:block" />
        </div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="relative w-full max-w-md"
        >
          <div className="glass rounded-2xl border border-border p-8 shadow-xl">
            {/* Header */}
            <div className="text-center mb-8">
              <Link
                href="/"
                className="inline-flex items-center gap-2 mb-6"
              >
                <Zap className="h-8 w-8 text-primary" />
                <span className="text-2xl font-bold font-display gradient-text">
                  Velocira
                </span>
              </Link>
              <h1 className="text-2xl font-bold font-display text-foreground">
                {t("auth.login.title")}
              </h1>
              <p className="text-sm text-foreground-secondary mt-1">
                {t("auth.login.subtitle")}
              </p>
            </div>

            {/* Error */}
            {error && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: "auto" }}
                className="mb-4 p-3 rounded-xl bg-error/10 border border-error/30 text-error text-sm"
              >
                {error}
              </motion.div>
            )}

            {/* Google Auth */}
            <GoogleAuthButton
              text={t("auth.login.google")}
              onError={setError}
            />

            {/* Divider */}
            <div className="relative my-6">
              <div className="absolute inset-0 flex items-center">
                <div className="w-full border-t border-border" />
              </div>
              <div className="relative flex justify-center text-xs">
                <span className="bg-card px-3 text-foreground-secondary">
                  {t("auth.login.orContinue")}
                </span>
              </div>
            </div>

            {/* Form */}
            <form onSubmit={handleSubmit} className="space-y-4">
              <Input
                label={t("auth.login.email")}
                type="email"
                icon={<Mail className="h-4 w-4" />}
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
              />

              <Input
                label={t("auth.login.password")}
                type="password"
                icon={<Lock className="h-4 w-4" />}
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete="current-password"
              />

              <div className="flex justify-end">
                <Link
                  href="/forgot-password"
                  className="text-sm text-primary hover:text-primary-hover transition-colors"
                >
                  {t("auth.login.forgot")}
                </Link>
              </div>

              <Button
                type="submit"
                className="w-full"
                size="lg"
                loading={isLoading}
              >
                {t("auth.login.submit")}
              </Button>
            </form>

            {/* Footer */}
            <p className="text-sm text-foreground-secondary text-center mt-6">
              {t("auth.login.noAccount")}{" "}
              <Link
                href="/register"
                className="text-primary hover:text-primary-hover font-medium transition-colors"
              >
                {t("auth.login.register")}
              </Link>
            </p>
          </div>
        </motion.div>
      </div>
    </PageTransition>
  );
}
