"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Mail, Lock, User } from "lucide-react";
import { useLocale } from "@/providers/LocaleProvider";
import { useAuthStore } from "@/store/authStore";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import GoogleAuthButton from "@/components/auth/GoogleAuthButton";
import AuthPageFrame from "@/components/auth/AuthPageFrame";
import { PageTransition } from "@/components/ui/Animations";

export default function RegisterPage() {
  const { t } = useLocale();
  const { register, error, setError } = useAuthStore();
  const router = useRouter();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!fullName || !email || !password) {
      setError(t("errors.required"));
      return;
    }
    const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
    if (!passwordRegex.test(password)) {
      setError(t("errors.passwordWeak"));
      return;
    }
    setIsSubmitting(true);
    try {
      const result = await register({ fullName, email, password });
      if (result.success) router.push(result.needsVerification ? `/verify-email?email=${encodeURIComponent(email)}` : "/projects");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <PageTransition>
      <AuthPageFrame
        icon={User}
        eyebrow="Start a workspace"
        title={t("auth.register.title")}
        description={t("auth.register.subtitle")}
        footer={<p>{t("auth.register.hasAccount")} <Link href="/login">{t("auth.register.login")}</Link></p>}
      >
        <div>
          {error && <div role="alert" className="auth-page__notice auth-page__notice--error">{error}</div>}
          <GoogleAuthButton text={t("auth.register.google")} onError={setError} />
          <div className="auth-page__divider"><div /><span>{t("auth.register.orContinue")}</span></div>
          <form onSubmit={handleSubmit} className="space-y-4">
            <Input label={t("auth.register.fullName")} type="text" icon={<User className="h-4 w-4" />} placeholder="Omar Elrfaay" value={fullName} onChange={(event) => setFullName(event.target.value)} required autoComplete="name" />
            <Input label={t("auth.register.email")} type="email" icon={<Mail className="h-4 w-4" />} placeholder="you@example.com" value={email} onChange={(event) => setEmail(event.target.value)} required autoComplete="email" />
            <div><Input label={t("auth.register.password")} type="password" icon={<Lock className="h-4 w-4" />} placeholder="••••••••" value={password} onChange={(event) => setPassword(event.target.value)} required autoComplete="new-password" /><p className="mt-1 text-xs text-foreground-secondary">{t("auth.register.passwordHint")}</p></div>
            <Button type="submit" className="w-full" size="lg" loading={isSubmitting}>{t("auth.register.submit")}</Button>
          </form>
        </div>
      </AuthPageFrame>
    </PageTransition>
  );
}
