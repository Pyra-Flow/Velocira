"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { motion } from "framer-motion";
import {
  Plus,
  FileText,
  Settings,
  FolderOpen,
  ArrowRight,
  Shield,
  HelpCircle,
  Clock,
  CheckCircle2,
  Circle,
  Download,
  BarChart3,
  HardDrive,
  Lightbulb,
  ChevronRight,
  PenLine,
  Rocket,
  Zap,
  CalendarDays,
} from "lucide-react";
import { useLocale } from "@/providers/LocaleProvider";
import { useAuthStore } from "@/store/authStore";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import {
  FadeIn,
  StaggerContainer,
  StaggerItem,
  PageTransition,
} from "@/components/ui/Animations";

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Good Morning";
  if (hour < 18) return "Good Afternoon";
  return "Good Evening";
}

function formatDate(): string {
  return new Date().toLocaleDateString("en-US", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

function getInitials(name: string): string {
  return name
    .split(" ")
    .map((n) => n[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);
}

/* ------------------------------------------------------------------ */
/*  Static Data                                                        */
/* ------------------------------------------------------------------ */

const quickActions = [
  {
    label: "New Project",
    description: "Start a new AI-powered documentation project",
    icon: Plus,
    href: "/projects/new",
    color: "bg-primary/10 text-primary",
  },
  {
    label: "My Projects",
    description: "Browse and manage all your projects",
    icon: FolderOpen,
    href: "/projects",
    color: "bg-accent/10 text-accent",
  },
  {
    label: "View Documents",
    description: "Access generated documents and exports",
    icon: FileText,
    href: "/projects",
    color: "bg-info/10 text-info",
  },
  {
    label: "Settings",
    description: "Manage your account and preferences",
    icon: Settings,
    href: "/settings",
    color: "bg-warning/10 text-warning",
  },
  {
    label: "Help & Docs",
    description: "Guides, tutorials, and documentation",
    icon: HelpCircle,
    href: "/docs",
    color: "bg-success/10 text-success",
  },
];

const adminAction = {
  label: "Admin Panel",
  description: "Manage users, roles, and system settings",
  icon: Shield,
  href: "/admin",
  color: "bg-error/10 text-error",
};

const stats = [
  { label: "My Projects", value: "3", icon: FolderOpen, color: "text-primary" },
  { label: "Documents", value: "12", icon: FileText, color: "text-accent" },
  { label: "Exports", value: "5", icon: Download, color: "text-info" },
  { label: "Storage", value: "45 MB", icon: HardDrive, color: "text-warning" },
];

const recentProjects = [
  {
    name: "E-Commerce Platform",
    status: "Complete",
    type: "Web App",
    lastModified: "Feb 15, 2026",
    progress: 100,
    statusColor: "bg-success/15 text-success",
  },
  {
    name: "AI Chat Assistant",
    status: "Generating",
    type: "AI System",
    lastModified: "Mar 1, 2026",
    progress: 63,
    statusColor: "bg-warning/15 text-warning",
  },
  {
    name: "Campus Navigator",
    status: "Complete",
    type: "Mobile App",
    lastModified: "Jan 20, 2026",
    progress: 100,
    statusColor: "bg-success/15 text-success",
  },
];

const checklistItems = [
  { label: "Create your account", done: true },
  { label: "Verify your email", done: true },
  { label: "Create your first project", done: false },
  { label: "Generate your first document", done: false },
  { label: "Export a document", done: false },
];

const activities = [
  {
    action: "Exported SRS as PDF",
    time: "2 hours ago",
    icon: Download,
    color: "text-info",
  },
  {
    action: "Generated API documentation",
    time: "5 hours ago",
    icon: Zap,
    color: "text-primary",
  },
  {
    action: "Created project 'AI Chat Assistant'",
    time: "1 day ago",
    icon: Plus,
    color: "text-success",
  },
  {
    action: "Edited ERD for E-Commerce Platform",
    time: "2 days ago",
    icon: PenLine,
    color: "text-accent",
  },
  {
    action: "Registered account",
    time: "1 week ago",
    icon: Rocket,
    color: "text-warning",
  },
];

const tips = [
  "💡 You can regenerate any individual section of your SRS without losing the rest of the document.",
  "🚀 Try describing your project in plain language — Velocira understands context and generates smarter docs.",
  "📄 Export to PDF, DOCX, or Markdown with a single click from any project page.",
  "🔍 Use the search bar in your project list to quickly find any document or project.",
  "🎯 Add detailed prompts when regenerating sections for more precise AI output.",
  "⚡ Velocira supports ERDs, API docs, use cases, and more — all generated from your project description.",
  "🛡️ Your documents are private by default. Only you can see them unless you share explicitly.",
];

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function HomePage() {
  const { t } = useLocale();
  const { user, isAuthenticated, isLoading } = useAuthStore();
  const router = useRouter();
  const [currentTip, setCurrentTip] = useState(0);

  /* Rotate tips */
  useEffect(() => {
    const interval = setInterval(() => {
      setCurrentTip((prev) => (prev + 1) % tips.length);
    }, 6000);
    return () => clearInterval(interval);
  }, []);

  /* Auth guard */
  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.push("/login");
    }
  }, [isLoading, isAuthenticated, router]);

  if (isLoading || !isAuthenticated) {
    return (
      <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center">
        <motion.div
          animate={{ rotate: 360 }}
          transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
          className="h-8 w-8 border-2 border-primary border-t-transparent rounded-full"
        />
      </div>
    );
  }

  const firstName = user?.fullName?.split(" ")[0] || "User";
  const greeting = getGreeting();
  const todayDate = formatDate();
  const actions =
    user?.role === "ADMIN" ? [...quickActions, adminAction] : quickActions;
  const completedSteps = checklistItems.filter((i) => i.done).length;
  const checklistProgress = (completedSteps / checklistItems.length) * 100;

  return (
    <PageTransition>
      <section className="relative overflow-hidden py-10 sm:py-14 pb-20">
        {/* Background effects */}
        
        

        <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-10">
          {/* ============================================================ */}
          {/* 1. Welcome Header                                            */}
          {/* ============================================================ */}
          <FadeIn>
            <div className="flex flex-col sm:flex-row sm:items-center gap-5">
              {/* Avatar */}
              <motion.div
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ type: "spring", stiffness: 200, damping: 15 }}
                className="h-16 w-16 rounded-2xl bg-primary/10 border border-primary/30 flex items-center justify-center shrink-0"
              >
                <span className="text-xl font-bold text-primary font-display">
                  {getInitials(user?.fullName || "U")}
                </span>
              </motion.div>

              <div className="flex-1">
                <motion.div
                  initial={{ scale: 0.9 }}
                  animate={{ scale: 1 }}
                  className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-primary/20 bg-primary/[0.06] text-primary text-xs font-medium mb-2"
                >
                  <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
                  {greeting}
                </motion.div>
                <h1 className="text-3xl sm:text-4xl font-bold font-display text-foreground">
                  Welcome back,{" "}
                  <span className="gradient-text">{firstName}</span>!
                </h1>
                <p className="text-foreground-secondary mt-1 flex items-center gap-2">
                  <CalendarDays className="h-4 w-4" />
                  {todayDate}
                </p>
              </div>

              {/* Quick CTA */}
              <div className="shrink-0">
                <Link href="/projects/new">
                  <Button
                    variant="primary"
                    size="lg"
                    icon={<Plus className="h-5 w-5" />}
                  >
                    New Project
                  </Button>
                </Link>
              </div>
            </div>
          </FadeIn>

          {/* ============================================================ */}
          {/* 2. Quick Stats Row                                           */}
          {/* ============================================================ */}
          <StaggerContainer className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {stats.map(({ label, value, icon: Icon, color }) => (
              <StaggerItem key={label}>
                <Card hover className="group">
                  <div className="flex items-center gap-3">
                    <div
                      className={`h-10 w-10 rounded-xl bg-background-secondary flex items-center justify-center shrink-0 transition-all`}
                    >
                      <Icon className={`h-5 w-5 ${color}`} />
                    </div>
                    <div>
                      <p className="text-2xl font-bold font-display text-foreground">
                        {value}
                      </p>
                      <p className="text-xs text-foreground-secondary">
                        {label}
                      </p>
                    </div>
                  </div>
                </Card>
              </StaggerItem>
            ))}
          </StaggerContainer>

          {/* ============================================================ */}
          {/* 3. Quick Actions                                             */}
          {/* ============================================================ */}
          <div>
            <FadeIn delay={0.05}>
              <h2 className="text-xl font-semibold font-display text-foreground mb-4">
                Quick Actions
              </h2>
            </FadeIn>

            <StaggerContainer className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {actions.map(
                ({ label, description, icon: Icon, href, color }) => (
                  <StaggerItem key={label}>
                    <Link href={href}>
                      <Card hover className="group cursor-pointer h-full">
                        <div className="flex items-center gap-4">
                          <div
                            className={`h-12 w-12 rounded-xl ${color} flex items-center justify-center shrink-0 transition-all`}
                          >
                            <Icon className="h-6 w-6" />
                          </div>
                          <div className="flex-1 min-w-0">
                            <h3 className="font-medium text-foreground">
                              {label}
                            </h3>
                            <p className="text-xs text-foreground-secondary mt-0.5 truncate">
                              {description}
                            </p>
                          </div>
                          <ArrowRight className="h-5 w-5 text-foreground-secondary group-hover:text-primary group-hover:translate-x-1 transition-all shrink-0" />
                        </div>
                      </Card>
                    </Link>
                  </StaggerItem>
                )
              )}
            </StaggerContainer>
          </div>

          {/* ============================================================ */}
          {/* 4. Recent Projects                                           */}
          {/* ============================================================ */}
          <div>
            <FadeIn delay={0.1}>
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-xl font-semibold font-display text-foreground">
                  Recent Projects
                </h2>
                <Link
                  href="/projects"
                  className="text-sm text-primary hover:underline flex items-center gap-1"
                >
                  View All Projects
                  <ChevronRight className="h-4 w-4" />
                </Link>
              </div>
            </FadeIn>

            <StaggerContainer className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {recentProjects.map((project) => (
                <StaggerItem key={project.name}>
                  <Card hover className="group cursor-pointer h-full flex flex-col">
                    <div className="flex items-start justify-between mb-3">
                      <div className="flex-1 min-w-0">
                        <h3 className="font-semibold text-foreground truncate">
                          {project.name}
                        </h3>
                        <p className="text-xs text-foreground-secondary mt-0.5">
                          {project.type}
                        </p>
                      </div>
                      <span
                        className={`text-xs font-medium px-2.5 py-1 rounded-full shrink-0 ms-2 ${project.statusColor}`}
                      >
                        {project.status}
                      </span>
                    </div>

                    {/* Progress bar for generating projects */}
                    {project.status === "Generating" && (
                      <div className="mb-3">
                        <div className="flex justify-between text-xs text-foreground-secondary mb-1">
                          <span>Generating...</span>
                          <span>{project.progress}%</span>
                        </div>
                        <div className="h-1.5 bg-background-secondary rounded-full overflow-hidden">
                          <motion.div
                            className="h-full bg-warning rounded-full"
                            initial={{ width: 0 }}
                            animate={{ width: `${project.progress}%` }}
                            transition={{ duration: 1.2, ease: "easeOut" }}
                          />
                        </div>
                      </div>
                    )}

                    <div className="flex items-center justify-between mt-auto pt-3 border-t border-border">
                      <span className="text-xs text-foreground-secondary flex items-center gap-1.5">
                        <Clock className="h-3.5 w-3.5" />
                        {project.lastModified}
                      </span>
                      <Button variant="ghost" size="sm">
                        Open
                        <ArrowRight className="h-3.5 w-3.5 ms-1" />
                      </Button>
                    </div>
                  </Card>
                </StaggerItem>
              ))}
            </StaggerContainer>
          </div>

          {/* ============================================================ */}
          {/* Two-column: Checklist + Activity                             */}
          {/* ============================================================ */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* ========================================================== */}
            {/* 5. Getting Started Checklist                                */}
            {/* ========================================================== */}
            <FadeIn delay={0.15}>
              <Card className="h-full">
                <div className="flex items-center gap-3 mb-5">
                  <div className="h-10 w-10 rounded-xl bg-primary/10 flex items-center justify-center">
                    <Rocket className="h-5 w-5 text-primary" />
                  </div>
                  <div>
                    <h2 className="text-lg font-semibold font-display text-foreground">
                      Getting Started
                    </h2>
                    <p className="text-xs text-foreground-secondary">
                      {completedSteps}/{checklistItems.length} complete
                    </p>
                  </div>
                </div>

                {/* Progress bar */}
                <div className="mb-5">
                  <div className="h-2 bg-background-secondary rounded-full overflow-hidden">
                    <motion.div
                      className="h-full bg-primary rounded-full"
                      initial={{ width: 0 }}
                      animate={{ width: `${checklistProgress}%` }}
                      transition={{ duration: 1, ease: "easeOut", delay: 0.3 }}
                    />
                  </div>
                </div>

                {/* Checklist items */}
                <div className="space-y-3">
                  {checklistItems.map((item, idx) => (
                    <motion.div
                      key={item.label}
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: 0.1 * idx + 0.3 }}
                      className={`flex items-center gap-3 p-2.5 rounded-xl transition-colors ${
                        item.done
                          ? "bg-success/5"
                          : "bg-background-secondary/50"
                      }`}
                    >
                      {item.done ? (
                        <CheckCircle2 className="h-5 w-5 text-success shrink-0" />
                      ) : (
                        <Circle className="h-5 w-5 text-foreground-secondary/40 shrink-0" />
                      )}
                      <span
                        className={`text-sm ${
                          item.done
                            ? "text-foreground-secondary line-through"
                            : "text-foreground"
                        }`}
                      >
                        {item.label}
                      </span>
                    </motion.div>
                  ))}
                </div>
              </Card>
            </FadeIn>

            {/* ========================================================== */}
            {/* 6. Recent Activity Timeline                                 */}
            {/* ========================================================== */}
            <FadeIn delay={0.2}>
              <Card className="h-full">
                <div className="flex items-center gap-3 mb-5">
                  <div className="h-10 w-10 rounded-xl bg-accent/10 flex items-center justify-center">
                    <BarChart3 className="h-5 w-5 text-accent" />
                  </div>
                  <h2 className="text-lg font-semibold font-display text-foreground">
                    Recent Activity
                  </h2>
                </div>

                <div className="space-y-1">
                  {activities.map((activity, idx) => {
                    const Icon = activity.icon;
                    return (
                      <motion.div
                        key={activity.action}
                        initial={{ opacity: 0, x: 20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: 0.1 * idx + 0.3 }}
                        className="flex items-start gap-3 p-2.5 rounded-xl hover:bg-background-secondary/50 transition-colors"
                      >
                        <div
                          className={`h-8 w-8 rounded-lg bg-background-secondary flex items-center justify-center shrink-0 mt-0.5`}
                        >
                          <Icon className={`h-4 w-4 ${activity.color}`} />
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm text-foreground truncate">
                            {activity.action}
                          </p>
                          <p className="text-xs text-foreground-secondary flex items-center gap-1 mt-0.5">
                            <Clock className="h-3 w-3" />
                            {activity.time}
                          </p>
                        </div>
                      </motion.div>
                    );
                  })}
                </div>
              </Card>
            </FadeIn>
          </div>

          {/* ============================================================ */}
          {/* 7. Tips & Insights                                           */}
          {/* ============================================================ */}
          <FadeIn delay={0.25}>
            <Card className="glass overflow-hidden">
              <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4">
                <div className="h-12 w-12 rounded-xl bg-primary/10 flex items-center justify-center shrink-0">
                  <Lightbulb className="h-6 w-6 text-primary" />
                </div>

                <div className="flex-1 min-w-0">
                  <h3 className="text-sm font-semibold text-foreground mb-1">
                    Tips & Insights
                  </h3>
                  <motion.p
                    key={currentTip}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -10 }}
                    transition={{ duration: 0.4 }}
                    className="text-sm text-foreground-secondary"
                  >
                    {tips[currentTip]}
                  </motion.p>
                </div>

                {/* Dots indicator */}
                <div className="flex gap-1.5 shrink-0">
                  {tips.map((_, idx) => (
                    <button
                      key={idx}
                      onClick={() => setCurrentTip(idx)}
                      className={`h-2 w-2 rounded-full transition-all cursor-pointer ${
                        idx === currentTip
                          ? "bg-primary w-5"
                          : "bg-foreground-secondary/30 hover:bg-foreground-secondary/50"
                      }`}
                    />
                  ))}
                </div>
              </div>
            </Card>
          </FadeIn>

          {/* ============================================================ */}
          {/* Account Info Footer                                          */}
          {/* ============================================================ */}
          <FadeIn delay={0.3}>
            <Card className="bg-background-secondary/50">
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                <div className="flex items-center gap-4">
                  <div className="h-11 w-11 rounded-xl bg-primary/10 border border-primary/20 flex items-center justify-center shrink-0">
                    <span className="text-sm font-bold text-primary font-display">
                      {getInitials(user?.fullName || "U")}
                    </span>
                  </div>
                  <div>
                    <p className="font-medium text-foreground">
                      {user?.fullName}
                    </p>
                    <p className="text-xs text-foreground-secondary">
                      {user?.email}
                    </p>
                  </div>
                  <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-primary/10 text-primary">
                    {user?.role}
                  </span>
                  {user?.emailVerified && (
                    <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-success/10 text-success flex items-center gap-1">
                      <CheckCircle2 className="h-3 w-3" />
                      Verified
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-3">
                  <Link href="/settings">
                    <Button
                      variant="outline"
                      size="sm"
                      icon={<Settings className="h-4 w-4" />}
                    >
                      Settings
                    </Button>
                  </Link>
                  <Link href="/projects">
                    <Button
                      variant="primary"
                      size="sm"
                      icon={<FolderOpen className="h-4 w-4" />}
                    >
                      My Projects
                    </Button>
                  </Link>
                </div>
              </div>
            </Card>
          </FadeIn>
        </div>
      </section>
    </PageTransition>
  );
}
