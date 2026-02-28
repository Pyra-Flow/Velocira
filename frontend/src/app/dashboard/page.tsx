"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import {
  FolderOpen,
  FileText,
  Clock,
  Plus,
  Mail,
  Calendar,
  CheckCircle,
  XCircle,
  TrendingUp,
  ArrowRight,
  Download,
  Settings,
  Rocket,
  Users,
  GitBranch,
  Smartphone,
  PenLine,
  ExternalLink,
  Layers,
  ArrowUpRight,
  ArrowDownRight,
  Minus,
  Loader2,
  CheckCircle2,
} from "lucide-react";
import { useLocale } from "@/providers/LocaleProvider";
import { useAuthStore } from "@/store/authStore";
import { useProjectStore } from "@/store/projectStore";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import {
  FadeIn,
  StaggerContainer,
  StaggerItem,
  PageTransition,
} from "@/components/ui/Animations";
import { formatDate, formatRelativeTime, getInitials } from "@/lib/utils";
import type { ProjectResponse, ProjectStatus } from "@/lib/api";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

type TabFilter = "all" | "active" | "completed";

/* ------------------------------------------------------------------ */
/*  Static Data (non-API)                                              */
/* ------------------------------------------------------------------ */

const upcomingFeatures = [
  {
    icon: Users,
    title: "Team Collaboration",
    description: "Invite teammates and work on documentation together.",
  },
  {
    icon: GitBranch,
    title: "Document Versioning",
    description: "Track changes and revert to any previous version.",
  },
  {
    icon: Smartphone,
    title: "Mobile App",
    description: "Access and manage projects on the go.",
  },
  {
    icon: PenLine,
    title: "Real-time Editing",
    description: "Collaborate with live cursors and instant sync.",
  },
];

const quickActions = [
  {
    icon: Plus,
    label: "New Project",
    href: "/projects/new",
    color: "text-primary",
    bgColor: "bg-primary/10",
  },
  {
    icon: Download,
    label: "Export All",
    href: "#",
    color: "text-info",
    bgColor: "bg-info/10",
  },
  {
    icon: Settings,
    label: "Settings",
    href: "/settings",
    color: "text-foreground-secondary",
    bgColor: "bg-background-secondary",
  },
];

/* ------------------------------------------------------------------ */
/*  Helper: Trend Indicator                                            */
/* ------------------------------------------------------------------ */

function TrendIndicator({
  trend,
  label,
}: {
  trend: "up" | "down" | "stable";
  label: string;
}) {
  const Icon =
    trend === "up"
      ? ArrowUpRight
      : trend === "down"
        ? ArrowDownRight
        : Minus;

  const color =
    trend === "up"
      ? "text-success"
      : trend === "down"
        ? "text-error"
        : "text-foreground-secondary";

  return (
    <span className={`inline-flex items-center gap-1 text-xs font-medium ${color}`}>
      <Icon className="h-3 w-3" />
      {label}
    </span>
  );
}

/* ------------------------------------------------------------------ */
/*  Helper: Progress Bar                                               */
/* ------------------------------------------------------------------ */

function ProgressBar({ value, className }: { value: number; className?: string }) {
  return (
    <div className={`h-1.5 w-full rounded-full bg-background-secondary ${className ?? ""}`}>
      <motion.div
        initial={{ width: 0 }}
        animate={{ width: `${value}%` }}
        transition={{ duration: 0.8, ease: "easeOut" }}
        className={`h-full rounded-full ${
          value === 100 ? "bg-success" : "bg-primary"
        }`}
      />
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Helper: Status Badge                                               */
/* ------------------------------------------------------------------ */

function StatusBadge({ status }: { status: ProjectStatus }) {
  const map: Record<ProjectStatus, { label: string; className: string }> = {
    DRAFT: {
      label: "Draft",
      className: "bg-foreground-secondary/10 text-foreground-secondary border-border",
    },
    GENERATING: {
      label: "Generating",
      className: "bg-warning/10 text-warning border-warning/20",
    },
    COMPLETE: {
      label: "Complete",
      className: "bg-success/10 text-success border-success/20",
    },
    FAILED: {
      label: "Failed",
      className: "bg-error/10 text-error border-error/20",
    },
  };

  const { label, className } = map[status] ?? {
    label: status,
    className: "bg-foreground-secondary/10 text-foreground-secondary border-border",
  };

  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border ${className}`}
    >
      {label}
    </span>
  );
}

/* ================================================================== */
/*  Page Component                                                     */
/* ================================================================== */

export default function DashboardPage() {
  const { t, locale } = useLocale();
  const { user, isAuthenticated, isLoading } = useAuthStore();
  const {
    projects,
    totalElements,
    isLoading: projectsLoading,
    fetchProjects,
  } = useProjectStore();
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<TabFilter>("all");

  /* ---- Protected Route ---- */
  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.push("/login");
    }
  }, [isLoading, isAuthenticated, router]);

  /* ---- Fetch projects on mount ---- */
  useEffect(() => {
    if (isAuthenticated) {
      fetchProjects({ page: 0, size: 8 });
    }
  }, [isAuthenticated, fetchProjects]);

  if (isLoading || !isAuthenticated || !user) {
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

  /* ---- Derived stats ---- */
  const totalDocs = projects.reduce((sum, p) => sum + (p.documentCount ?? 0), 0);
  const completedCount = projects.filter((p) => p.status === "COMPLETE").length;
  const activeCount = projects.filter(
    (p) => p.status === "DRAFT" || p.status === "GENERATING"
  ).length;

  const statCards = [
    {
      key: "totalProjects",
      label: "Total Projects",
      value: String(totalElements),
      icon: FolderOpen,
      trend: "up" as const,
      trendLabel: `${activeCount} active`,
      color: "text-primary",
      bgColor: "bg-primary/10",
    },
    {
      key: "totalDocs",
      label: "Documents",
      value: String(totalDocs),
      icon: FileText,
      trend: "up" as const,
      trendLabel: "across all projects",
      color: "text-accent",
      bgColor: "bg-accent/10",
    },
    {
      key: "completed",
      label: "Completed",
      value: String(completedCount),
      icon: CheckCircle2,
      trend: "stable" as const,
      trendLabel: `of ${totalElements}`,
      color: "text-success",
      bgColor: "bg-success/10",
    },
    {
      key: "progress",
      label: "Avg. Progress",
      value:
        projects.length > 0
          ? `${Math.round(projects.reduce((s, p) => s + p.progress, 0) / projects.length)}%`
          : "—",
      icon: TrendingUp,
      trend: "up" as const,
      trendLabel: "across projects",
      color: "text-info",
      bgColor: "bg-info/10",
    },
  ];

  /* ---- Filtered Projects ---- */
  const filteredProjects =
    activeTab === "all"
      ? projects
      : activeTab === "active"
        ? projects.filter((p) => p.status === "DRAFT" || p.status === "GENERATING")
        : projects.filter((p) => p.status === "COMPLETE");

  const tabs: { key: TabFilter; label: string; count: number }[] = [
    { key: "all", label: "All", count: projects.length },
    {
      key: "active",
      label: "Active",
      count: projects.filter(
        (p) => p.status === "DRAFT" || p.status === "GENERATING"
      ).length,
    },
    {
      key: "completed",
      label: "Completed",
      count: projects.filter((p) => p.status === "COMPLETE").length,
    },
  ];

  /* ---- Type badge color ---- */
  const typeBadgeColor = (type: string) => {
    const map: Record<string, string> = {
      WEB_APP: "bg-primary/10 text-primary",
      MOBILE_APP: "bg-success/10 text-success",
      AI_SYSTEM: "bg-accent/10 text-accent",
      IOT: "bg-warning/10 text-warning",
      DESKTOP_APP: "bg-info/10 text-info",
      API_BACKEND: "bg-error/10 text-error",
    };
    return map[type] ?? "bg-foreground-secondary/10 text-foreground-secondary";
  };

  const formatType = (type: string) =>
    type
      .replace(/_/g, " ")
      .replace(/\b\w/g, (c) => c.toUpperCase());

  /* ================================================================ */
  /*  Render                                                           */
  /* ================================================================ */

  return (
    <PageTransition>
      <section className="relative py-12 sm:py-16">
        {/* Background decorations */}
        <div className="absolute top-0 end-1/4 w-72 h-72 bg-primary/10 rounded-full blur-3xl" />
        <div className="absolute bottom-1/4 start-0 w-56 h-56 bg-accent/10 rounded-full blur-3xl" />

        <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          {/* -------------------------------------------------------- */}
          {/*  1. Header                                               */}
          {/* -------------------------------------------------------- */}
          <FadeIn>
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-10">
              <div>
                <h1 className="text-3xl sm:text-4xl font-bold font-display gradient-text">
                  {t("dashboard.title")}
                </h1>
                <p className="text-foreground-secondary mt-1 text-sm sm:text-base">
                  {t("dashboard.subtitle")}
                </p>
              </div>
              <Link href="/projects/new">
                <Button size="md" icon={<Plus className="h-4 w-4" />}>
                  New Project
                </Button>
              </Link>
            </div>
          </FadeIn>

          {/* -------------------------------------------------------- */}
          {/*  2. Stats Grid                                           */}
          {/* -------------------------------------------------------- */}
          <StaggerContainer className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-10">
            {statCards.map((stat) => {
              const Icon = stat.icon;
              return (
                <StaggerItem key={stat.key}>
                  <Card hover>
                    <div className="flex items-start justify-between mb-3">
                      <div
                        className={`h-10 w-10 rounded-xl ${stat.bgColor} flex items-center justify-center shrink-0`}
                      >
                        <Icon className={`h-5 w-5 ${stat.color}`} />
                      </div>
                      <TrendIndicator trend={stat.trend} label={stat.trendLabel} />
                    </div>
                    {projectsLoading ? (
                      <Loader2 className="h-5 w-5 animate-spin text-foreground-secondary" />
                    ) : (
                      <>
                        <p className="text-2xl font-bold text-foreground">{stat.value}</p>
                        <p className="text-xs text-foreground-secondary mt-0.5">
                          {stat.label}
                        </p>
                      </>
                    )}
                  </Card>
                </StaggerItem>
              );
            })}
          </StaggerContainer>

          {/* -------------------------------------------------------- */}
          {/*  3. Main Grid: Projects + Sidebar                        */}
          {/* -------------------------------------------------------- */}
          <div className="grid lg:grid-cols-3 gap-6 mb-10">
            {/* ---- Projects Section (2/3 width) ---- */}
            <FadeIn direction="right" className="lg:col-span-2">
              <Card className="h-full">
                {/* Projects header */}
                <div className="flex items-center justify-between mb-6">
                  <h2 className="text-lg font-semibold text-foreground">
                    {t("dashboard.projects.title")}
                  </h2>
                  <Link
                    href="/projects"
                    className="text-sm text-primary hover:underline inline-flex items-center gap-1"
                  >
                    View All <ArrowRight className="h-3.5 w-3.5" />
                  </Link>
                </div>

                {/* Tab filters */}
                <div className="flex items-center gap-1 mb-6 p-1 rounded-xl bg-background-secondary border border-border w-fit">
                  {tabs.map((tab) => (
                    <button
                      key={tab.key}
                      onClick={() => setActiveTab(tab.key)}
                      className={`relative px-4 py-1.5 rounded-lg text-sm font-medium transition-all cursor-pointer ${
                        activeTab === tab.key
                          ? "text-primary"
                          : "text-foreground-secondary hover:text-foreground"
                      }`}
                    >
                      {activeTab === tab.key && (
                        <motion.div
                          layoutId="activeTab"
                          className="absolute inset-0 bg-card border border-border rounded-lg shadow-sm"
                          transition={{ type: "spring", stiffness: 500, damping: 35 }}
                        />
                      )}
                      <span className="relative z-10">
                        {tab.label}{" "}
                        <span className="text-xs opacity-60">({tab.count})</span>
                      </span>
                    </button>
                  ))}
                </div>

                {/* Project cards */}
                {projectsLoading ? (
                  <div className="flex items-center justify-center py-16">
                    <Loader2 className="h-6 w-6 animate-spin text-primary" />
                  </div>
                ) : filteredProjects.length === 0 ? (
                  <div className="text-center py-16">
                    <FolderOpen className="h-10 w-10 text-foreground-secondary/40 mx-auto mb-3" />
                    <p className="text-sm text-foreground-secondary">
                      No projects yet.{" "}
                      <Link href="/projects/new" className="text-primary hover:underline">
                        Create your first project
                      </Link>
                    </p>
                  </div>
                ) : (
                  <AnimatePresence mode="wait">
                    <motion.div
                      key={activeTab}
                      initial={{ opacity: 0, y: 8 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, y: -8 }}
                      transition={{ duration: 0.2 }}
                      className="space-y-4"
                    >
                      {filteredProjects.slice(0, 4).map((project) => (
                        <Link href={`/projects/${project.id}`} key={project.id}>
                          <motion.div
                            whileHover={{ scale: 1.005 }}
                            className="group p-4 rounded-xl border border-border bg-card hover:border-border-hover hover:bg-card-hover transition-all cursor-pointer"
                          >
                            <div className="flex items-start justify-between gap-4 mb-3">
                              <div className="min-w-0 flex-1">
                                <div className="flex items-center gap-2 mb-1 flex-wrap">
                                  <h3 className="font-semibold text-foreground group-hover:text-primary transition-colors">
                                    {project.name}
                                  </h3>
                                  <span
                                    className={`inline-flex items-center px-2 py-0.5 text-[10px] font-medium rounded-full ${typeBadgeColor(project.type)}`}
                                  >
                                    {formatType(project.type)}
                                  </span>
                                  <StatusBadge status={project.status} />
                                </div>
                                <p className="text-sm text-foreground-secondary line-clamp-1">
                                  {project.description}
                                </p>
                              </div>
                              <ExternalLink className="h-4 w-4 text-foreground-secondary/40 group-hover:text-primary shrink-0 mt-1 transition-colors" />
                            </div>

                            <div className="flex items-center justify-between gap-4">
                              <div className="flex items-center gap-4 text-xs text-foreground-secondary">
                                <span className="flex items-center gap-1">
                                  <FileText className="h-3.5 w-3.5" />
                                  {project.documentCount} docs
                                </span>
                                <span className="flex items-center gap-1">
                                  <Clock className="h-3.5 w-3.5" />
                                  {formatRelativeTime(project.updatedAt)}
                                </span>
                              </div>
                              <div className="flex items-center gap-2 min-w-[120px]">
                                <ProgressBar value={project.progress} className="flex-1" />
                                <span className="text-xs font-medium text-foreground-secondary">
                                  {project.progress}%
                                </span>
                              </div>
                            </div>
                          </motion.div>
                        </Link>
                      ))}
                    </motion.div>
                  </AnimatePresence>
                )}
              </Card>
            </FadeIn>

            {/* ---- Sidebar (1/3 width) ---- */}
            <div className="space-y-6">
              {/* Profile Card */}
              <FadeIn direction="left" delay={0.1}>
                <Card>
                  <div className="flex items-center justify-between mb-4">
                    <h2 className="text-lg font-semibold text-foreground">
                      {t("dashboard.profile.title")}
                    </h2>
                    <Link
                      href="/settings"
                      className="text-xs text-primary hover:underline"
                    >
                      Edit Profile
                    </Link>
                  </div>

                  <div className="flex items-center gap-4 mb-5">
                    <motion.div
                      whileHover={{ scale: 1.1, rotate: 5 }}
                      className="h-14 w-14 rounded-2xl bg-gradient-to-br from-primary to-accent flex items-center justify-center text-white font-bold text-lg shadow-lg shrink-0"
                    >
                      {getInitials(user.fullName)}
                    </motion.div>
                    <div className="min-w-0">
                      <p className="font-semibold text-foreground truncate">
                        {user.fullName}
                      </p>
                      <p className="text-sm text-foreground-secondary capitalize">
                        {user.role.toLowerCase()}
                      </p>
                    </div>
                  </div>

                  <div className="space-y-3">
                    <div className="flex items-center gap-3 text-sm">
                      <Mail className="h-4 w-4 text-foreground-secondary shrink-0" />
                      <span className="text-foreground truncate">{user.email}</span>
                    </div>
                    <div className="flex items-center gap-3 text-sm">
                      {user.emailVerified ? (
                        <CheckCircle className="h-4 w-4 text-success shrink-0" />
                      ) : (
                        <XCircle className="h-4 w-4 text-error shrink-0" />
                      )}
                      <span
                        className={
                          user.emailVerified ? "text-success" : "text-error"
                        }
                      >
                        {user.emailVerified
                          ? t("dashboard.profile.verified")
                          : t("dashboard.profile.notVerified")}
                      </span>
                    </div>
                    <div className="flex items-center gap-3 text-sm">
                      <Calendar className="h-4 w-4 text-foreground-secondary shrink-0" />
                      <span className="text-foreground-secondary">Member since</span>
                      <span className="text-foreground">
                        {user.createdAt
                          ? formatDate(user.createdAt)
                          : "—"}
                      </span>
                    </div>
                    {user.lastLoginAt && (
                      <div className="flex items-center gap-3 text-sm">
                        <Clock className="h-4 w-4 text-foreground-secondary shrink-0" />
                        <span className="text-foreground-secondary">Last login</span>
                        <span className="text-foreground">
                          {formatDate(user.lastLoginAt)}
                        </span>
                      </div>
                    )}
                  </div>
                </Card>
              </FadeIn>

              {/* Quick Actions */}
              <FadeIn direction="left" delay={0.15}>
                <Card>
                  <h2 className="text-lg font-semibold text-foreground mb-4">
                    Quick Actions
                  </h2>
                  <div className="space-y-2">
                    {quickActions.map((action) => {
                      const Icon = action.icon;
                      return (
                        <Link href={action.href} key={action.label}>
                          <motion.div
                            whileHover={{ x: 4 }}
                            className="flex items-center gap-3 p-3 rounded-xl hover:bg-background-secondary transition-colors cursor-pointer group"
                          >
                            <div
                              className={`h-9 w-9 rounded-lg ${action.bgColor} flex items-center justify-center shrink-0`}
                            >
                              <Icon className={`h-4 w-4 ${action.color}`} />
                            </div>
                            <span className="text-sm font-medium text-foreground group-hover:text-primary transition-colors">
                              {action.label}
                            </span>
                            <ArrowRight className="h-3.5 w-3.5 text-foreground-secondary/40 ms-auto group-hover:text-primary transition-colors" />
                          </motion.div>
                        </Link>
                      );
                    })}
                  </div>
                </Card>
              </FadeIn>

              {/* Project Type Distribution */}
              <FadeIn direction="left" delay={0.2}>
                <Card>
                  <div className="flex items-center gap-2 mb-4">
                    <Layers className="h-5 w-5 text-primary" />
                    <h2 className="text-lg font-semibold text-foreground">
                      Project Types
                    </h2>
                  </div>
                  {projects.length === 0 ? (
                    <p className="text-sm text-foreground-secondary text-center py-4">
                      No data yet
                    </p>
                  ) : (
                    <>
                      <div className="space-y-3">
                        {Object.entries(
                          projects.reduce<Record<string, number>>((acc, p) => {
                            acc[p.type] = (acc[p.type] ?? 0) + 1;
                            return acc;
                          }, {})
                        )
                          .sort((a, b) => b[1] - a[1])
                          .map(([type, count]) => {
                            const max = projects.length;
                            return (
                              <div key={type}>
                                <div className="flex items-center justify-between mb-1">
                                  <span className="text-xs text-foreground-secondary">
                                    {formatType(type)}
                                  </span>
                                  <span className="text-xs font-semibold text-foreground">
                                    {count}
                                  </span>
                                </div>
                                <div className="h-2 w-full rounded-full bg-background-secondary">
                                  <motion.div
                                    initial={{ width: 0 }}
                                    whileInView={{
                                      width: `${(count / max) * 100}%`,
                                    }}
                                    viewport={{ once: true }}
                                    transition={{ duration: 0.6, ease: "easeOut" }}
                                    className="h-full rounded-full bg-primary"
                                  />
                                </div>
                              </div>
                            );
                          })}
                      </div>
                      <div className="mt-4 pt-3 border-t border-border">
                        <p className="text-xs text-foreground-secondary text-center">
                          <span className="font-semibold text-foreground">
                            {totalElements}
                          </span>{" "}
                          total projects
                        </p>
                      </div>
                    </>
                  )}
                </Card>
              </FadeIn>
            </div>
          </div>

          {/* -------------------------------------------------------- */}
          {/*  4. Upcoming Features                                    */}
          {/* -------------------------------------------------------- */}
          <FadeIn>
            <Card glow>
              <div className="flex items-center gap-2 mb-6">
                <Rocket className="h-5 w-5 text-primary" />
                <h2 className="text-lg font-semibold text-foreground">
                  Coming Soon
                </h2>
                <span className="ms-auto text-[10px] font-semibold uppercase tracking-wider text-primary bg-primary/10 px-2.5 py-0.5 rounded-full">
                  Roadmap
                </span>
              </div>

              <StaggerContainer className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
                {upcomingFeatures.map((feature) => {
                  const Icon = feature.icon;
                  return (
                    <StaggerItem key={feature.title}>
                      <div className="group p-4 rounded-xl border border-border bg-background-secondary hover:border-primary/30 hover:bg-card-hover transition-all text-center">
                        <div className="h-10 w-10 rounded-xl bg-primary/10 flex items-center justify-center mx-auto mb-3 group-hover:scale-110 transition-transform">
                          <Icon className="h-5 w-5 text-primary" />
                        </div>
                        <h3 className="text-sm font-semibold text-foreground mb-1">
                          {feature.title}
                        </h3>
                        <p className="text-xs text-foreground-secondary leading-relaxed">
                          {feature.description}
                        </p>
                      </div>
                    </StaggerItem>
                  );
                })}
              </StaggerContainer>

              <div className="mt-6 pt-4 border-t border-border text-center">
                <p className="text-sm text-foreground-secondary">
                  Have a feature request?{" "}
                  <Link
                    href="/feedback"
                    className="text-primary hover:underline font-medium"
                  >
                    Let us know →
                  </Link>
                </p>
              </div>
            </Card>
          </FadeIn>
        </div>
      </section>
    </PageTransition>
  );
}
