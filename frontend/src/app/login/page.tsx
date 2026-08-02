"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Mail, Lock } from "lucide-react";
import { useLocale } from "@/providers/LocaleProvider";
import { useAuthStore } from "@/store/authStore";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import GoogleAuthButton from "@/components/auth/GoogleAuthButton";
import AuthPageFrame from "@/components/auth/AuthPageFrame";
import { PageTransition } from "@/components/ui/Animations";

export default function LoginPage() {
  const { t } = useLocale();
  const { login, error, setError } = useAuthStore();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!email || !password) {
      setError(t("errors.required"));
      return;
    }
    setIsSubmitting(true);
    try {
      if (await login(email, password)) router.push("/home");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <PageTransition>
      <AuthPageFrame
        icon={Lock}
        eyebrow="Account access"
        title={t("auth.login.title")}
        description={t("auth.login.subtitle")}
        footer={<p>{t("auth.login.noAccount")} <Link href="/register">{t("auth.login.register")}</Link></p>}
      >
        <div>
          {error && <div role="alert" className="auth-page__notice auth-page__notice--error">{error}</div>}
          <GoogleAuthButton text={t("auth.login.google")} onError={setError} />
          <div className="auth-page__divider"><div /><span>{t("auth.login.orContinue")}</span></div>
          <form onSubmit={handleSubmit} className="space-y-4">
            <Input label={t("auth.login.email")} type="email" icon={<Mail className="h-4 w-4" />} placeholder="you@example.com" value={email} onChange={(event) => setEmail(event.target.value)} required autoComplete="email" />
            <Input label={t("auth.login.password")} type="password" icon={<Lock className="h-4 w-4" />} placeholder="••••••••" value={password} onChange={(event) => setPassword(event.target.value)} required autoComplete="current-password" />
            <div className="flex justify-end"><Link href="/forgot-password" className="text-sm text-accent hover:text-foreground transition-colors">{t("auth.login.forgot")}</Link></div>
            <Button type="submit" className="w-full" size="lg" loading={isSubmitting}>{t("auth.login.submit")}</Button>
          </form>
        </div>
      </AuthPageFrame>
    </PageTransition>
  );
}
