"use client";

import { useEffect, type ReactNode } from "react";
import { GoogleOAuthProvider } from "@react-oauth/google";
import { LocaleProvider } from "@/providers/LocaleProvider";
import { ThemeProvider, type Theme } from "@/providers/ThemeProvider";
import { useAuthStore } from "@/store/authStore";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import CursorFollower from "@/components/ui/CursorFollower";
import ScrollProgressBar from "@/components/ui/ScrollProgressBar";
function AuthInitializer({ children }: { children: ReactNode }) {
  const initialize = useAuthStore((s) => s.initialize);

  useEffect(() => {
    initialize();
  }, [initialize]);

  return <>{children}</>;
}

const GOOGLE_CLIENT_ID =
  process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID || "";

export default function ClientProviders({
  children,
  initialTheme,
}: {
  children: ReactNode;
  initialTheme: Theme;
}) {
  return (
    <GoogleOAuthProvider clientId={GOOGLE_CLIENT_ID}>
      <ThemeProvider initialTheme={initialTheme}>
        <LocaleProvider>
          <AuthInitializer>
            <CursorFollower />
            <ScrollProgressBar />
            <div className="min-h-screen flex flex-col relative">
              <Navbar />
              <main className="flex-1 pt-24">{children}</main>
              <Footer />
            </div>
          </AuthInitializer>
        </LocaleProvider>
      </ThemeProvider>
    </GoogleOAuthProvider>
  );
}
