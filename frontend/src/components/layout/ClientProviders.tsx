"use client";

import { useEffect, useMemo, useState, type ReactNode } from "react";
import { GoogleOAuthProvider } from "@react-oauth/google";
import { usePathname, useRouter } from "next/navigation";
import { MotionConfig } from "framer-motion";
import { FolderKanban, Plus, Settings, ShieldCheck } from "lucide-react";
import { LocaleProvider } from "@/providers/LocaleProvider";
import { ThemeProvider, type Theme } from "@/providers/ThemeProvider";
import { useAuthStore } from "@/store/authStore";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import WorkspaceHeader from "@/components/layout/WorkspaceHeader";
import ScrollProgressBar from "@/components/ui/ScrollProgressBar";
import CommandPalette, { type CommandPaletteAction } from "@/components/ui/CommandPalette";
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
  const pathname = usePathname();
  const router = useRouter();
  const { user } = useAuthStore();
  const [commandPaletteOpen, setCommandPaletteOpen] = useState(false);
  const isApplicationRoute = /^(\/dashboard|\/projects|\/settings|\/admin)(?:\/|$)/.test(pathname);
  const isAuthRoute = /^(\/login|\/logout|\/register|\/forgot-password|\/reset-password|\/verify-email)(?:\/|$)/.test(pathname);
  const isProjectDetailRoute = /^\/projects\/[^/]+$/.test(pathname);
  // The authenticated shell deliberately includes project detail routes so the
  // sidebar, utility header, theme toggle, and mobile navigation stay unified.
  const usesWorkspaceChrome = isApplicationRoute;
  const usesMarketingChrome = !isApplicationRoute && !isAuthRoute;
  const commandActions = useMemo<CommandPaletteAction[]>(() => {
    const actions: CommandPaletteAction[] = [
      { id: "projects", label: "Open projects", description: "Browse and filter project workspaces", group: "Navigate", icon: <FolderKanban className="h-4 w-4" />, onSelect: () => router.push("/projects") },
      { id: "new-project", label: "Create a project", description: "Start a guided documentation workspace", group: "Create", icon: <Plus className="h-4 w-4" />, shortcut: "N", onSelect: () => router.push("/projects/new") },
      { id: "settings", label: "Open settings", description: "Manage profile and account security", group: "Navigate", icon: <Settings className="h-4 w-4" />, onSelect: () => router.push("/settings") },
    ];
    if (user?.role === "ADMIN") actions.push({ id: "admin", label: "Open admin operations", description: "View platform analytics and audit activity", group: "Admin", icon: <ShieldCheck className="h-4 w-4" />, onSelect: () => router.push("/admin") });
    return actions;
  }, [router, user?.role]);

  return (
    <GoogleOAuthProvider clientId={GOOGLE_CLIENT_ID}>
      <ThemeProvider initialTheme={initialTheme}>
        <MotionConfig reducedMotion="user">
          <LocaleProvider>
            <AuthInitializer>
              <div className={`min-h-screen flex flex-col relative ${usesWorkspaceChrome ? "workspace-shell" : ""} ${isProjectDetailRoute ? "project-detail-shell" : ""}`}>
                {usesMarketingChrome && <><ScrollProgressBar /><Navbar /></>}
                {usesWorkspaceChrome && <WorkspaceHeader onOpenCommandPalette={() => setCommandPaletteOpen(true)} />}
                <main className={usesWorkspaceChrome || isAuthRoute ? "flex-1" : "flex-1 pt-16"}>{children}</main>
                {usesMarketingChrome && <Footer />}
              </div>
              {usesWorkspaceChrome && <CommandPalette actions={commandActions} open={commandPaletteOpen} onOpenChange={setCommandPaletteOpen} />}
            </AuthInitializer>
          </LocaleProvider>
        </MotionConfig>
      </ThemeProvider>
    </GoogleOAuthProvider>
  );
}
