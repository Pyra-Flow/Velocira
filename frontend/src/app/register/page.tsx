"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Mail, Lock, User, Building, Zap } from "lucide-react";
import { motion } from "framer-motion";
import { useLocale } from "@/providers/LocaleProvider";
import { useAuthStore } from "@/store/authStore";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import GoogleAuthButton from "@/components/auth/GoogleAuthButton";
import { PageTransition } from "@/components/ui/Animations";

export default function RegisterPage() {
  const { t } = useLocale();
  const { register, isLoading, error, setError } = useAuthStore();
  const router = useRouter();

  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [universityName, setUniversityName] = useState("");

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!fullName || !email || !password) {
      setError(t("errors.required"));
      return;
    }

    const passwordRegex =
      /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
    if (!passwordRegex.test(password)) {
      setError(t("errors.passwordWeak"));
      return;
    }

    const result = await register({
      fullName,
      email,
      password,
      universityName: universityName || undefined,
    });

    if (result.success) {
      if (result.needsVerification) {
        router.push(`/verify-email?email=${encodeURIComponent(email)}`);
      } else {
        router.push("/home");
      }
    }
  };

  return (
    <PageTransition>
      <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center px-4 py-12">
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          
          
          <div className="absolute top-16 left-[8%] w-24 h-24 border border-primary/[0.06] rotate-45 hidden lg:block" />
          <div className="absolute bottom-16 right-[10%] w-16 h-16 border border-primary/[0.05] rotate-45 hidden lg:block" />
        </div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
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
              <h1 className="text-2xl font-bold font-display text-foreground">
                {t("auth.register.title")}
              </h1>
              <p className="text-sm text-foreground-secondary mt-1">
                {t("auth.register.subtitle")}
              </p>
            </div>

            {error && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: "auto" }}
                className="mb-4 p-3 rounded-xl bg-error/10 border border-error/30 text-error text-sm"
              >
                {error}
              </motion.div>
            )}

            <GoogleAuthButton
              text={t("auth.register.google")}
              onError={setError}
            />

            <div className="relative my-6">
              <div className="absolute inset-0 flex items-center">
                <div className="w-full border-t border-border" />
              </div>
              <div className="relative flex justify-center text-xs">
                <span className="bg-card px-3 text-foreground-secondary">
                  {t("auth.register.orContinue")}
                </span>
              </div>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              <Input
                label={t("auth.register.fullName")}
                type="text"
                icon={<User className="h-4 w-4" />}
                placeholder="Omar Elrfaay"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
                autoComplete="name"
              />

              <Input
                label={t("auth.register.email")}
                type="email"
                icon={<Mail className="h-4 w-4" />}
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
              />

              <div>
                <Input
                  label={t("auth.register.password")}
                  type="password"
                  icon={<Lock className="h-4 w-4" />}
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  autoComplete="new-password"
                />
                <p className="text-xs text-foreground-secondary mt-1">
                  {t("auth.register.passwordHint")}
                </p>
              </div>

              <Input
                label={t("auth.register.universityName")}
                type="text"
                icon={<Building className="h-4 w-4" />}
                placeholder=""
                value={universityName}
                onChange={(e) => setUniversityName(e.target.value)}
                autoComplete="organization"
              />

              <Button
                type="submit"
                className="w-full"
                size="lg"
                loading={isLoading}
              >
                {t("auth.register.submit")}
              </Button>
            </form>

            <p className="text-sm text-foreground-secondary text-center mt-6">
              {t("auth.register.hasAccount")}{" "}
              <Link
                href="/login"
                className="text-primary hover:text-primary-hover font-medium transition-colors"
              >
                {t("auth.register.login")}
              </Link>
            </p>
          </div>
        </motion.div>
      </div>
    </PageTransition>
  );
}
