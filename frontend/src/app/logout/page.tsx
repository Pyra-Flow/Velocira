"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, LogOut, ShieldCheck, Sparkles } from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import { useAuthStore } from "@/store/authStore";

export default function LogoutPage() {
  const router = useRouter();
  const { user, isAuthenticated, isLoading, logout } = useAuthStore();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!isLoading && !isAuthenticated) router.replace("/");
  }, [isAuthenticated, isLoading, router]);

  const signOut = async () => {
    setSubmitting(true);
    await logout();
    router.replace("/");
  };

  if (isLoading || !isAuthenticated) return null;

  return (
    <section className="sf-system-state">
      <section className="relative w-full max-w-md">
        <Link href="/dashboard" className="mb-5 inline-flex items-center gap-1.5 text-sm text-foreground-secondary transition-colors hover:text-accent"><ArrowLeft className="h-4 w-4" /> Return to workspace</Link>
        <Card className="overflow-hidden p-0">
          <div className="border-b border-border-subtle bg-card-hover p-7">
            <div className="flex h-12 w-12 items-center justify-center rounded-md border border-accent bg-accent-light text-accent"><Sparkles className="h-5 w-5" /></div>
            <p className="sf-meta mt-5 text-accent">Velocira workspace</p>
            <h1 className="mt-2 text-2xl font-semibold tracking-tight text-foreground">Ready to sign out?</h1>
            <p className="mt-2 text-sm leading-6 text-foreground-secondary">You&apos;re signed in as <span className="font-medium text-foreground">{user?.fullName || user?.email}</span>. Your projects and generated documents stay safely saved.</p>
          </div>
          <div className="space-y-5 p-6 sm:p-7">
            <div className="flex gap-3 rounded-md border border-border bg-background-secondary/60 p-4 text-sm text-foreground-secondary"><ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-success" /><p>Signing out only ends this device session. It doesn&apos;t change your project data or remove other active sessions.</p></div>
            <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <Link href="/dashboard"><Button variant="outline" className="w-full sm:w-auto">Stay signed in</Button></Link>
              <Button className="w-full sm:w-auto" loading={submitting} disabled={submitting} icon={<LogOut className="h-4 w-4" />} onClick={() => void signOut()}>Sign out</Button>
            </div>
          </div>
        </Card>
      </section>
    </section>
  );
}
