"use client";

import { useState, useEffect } from "react";
import { motion } from "framer-motion";
import {
  Users,
  FolderKanban,
  FileText,
  Activity,
  TrendingUp,
  ArrowUpRight,
  ArrowDownRight,
  Shield,
  Download,
  ScrollText,
  ChevronRight,
  Server,
  Database,
  Brain,
  Circle,
  Clock,
  Mail,
  AlertTriangle,
  Loader2,
  ShieldCheck,
} from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLocale } from "@/providers/LocaleProvider";
import { useAuthStore } from "@/store/authStore";
import {
  adminApi,
  type AdminAnalyticsResponse,
  type AdminUserResponse,
} from "@/lib/api";
import { formatRelativeTime, getInitials } from "@/lib/utils";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import {
  FadeIn,
  StaggerContainer,
  StaggerItem,
  PageTransition,
} from "@/components/ui/Animations";

/* ------------------------------------------------------------------ */
/*  Static Data (platform health — not from API)                       */
/* ------------------------------------------------------------------ */

const platformHealth = [
  {
    service: "Backend API",
    status: "operational" as const,
    uptime: "99.98%",
    icon: Server,
  },
  {
    service: "ML Service",
    status: "degraded" as const,
    uptime: "97.12%",
    icon: Brain,
  },
  {
    service: "Database",
    status: "operational" as const,
    uptime: "99.99%",
    icon: Database,
  },
];

const statusColors = {
  operational: "text-emerald-400",
  degraded: "text-amber-400",
  down: "text-red-400",
};

const statusBg = {
  operational: "bg-emerald-500/20",
  degraded: "bg-amber-500/20",
  down: "bg-red-500/20",
};

const statusLabel = {
  operational: "Operational",
  degraded: "Degraded",
  down: "Down",
};

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

const formatType = (type: string) =>
  type
    .replace(/_/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function AdminDashboard() {
  const router = useRouter();
  const { t } = useLocale();
  const { user, isAuthenticated, isLoading } = useAuthStore();

  const [analytics, setAnalytics] = useState<AdminAnalyticsResponse | null>(null);
  const [recentUsers, setRecentUsers] = useState<AdminUserResponse[]>([]);
  const [loadingData, setLoadingData] = useState(true);

  /* ---- Auth guard ---- */
  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace("/login");
    }
  }, [isLoading, isAuthenticated, router]);

  /* ---- Fetch analytics + recent users ---- */
  useEffect(() => {
    if (!isAuthenticated || user?.role !== "ADMIN") return;

    const load = async () => {
      setLoadingData(true);
      try {
        const [analyticsRes, usersRes] = await Promise.all([
          adminApi.analytics(),
          adminApi.listUsers({ page: 0, size: 5 }),
        ]);

        if (analyticsRes.success && analyticsRes.data) {
          setAnalytics(analyticsRes.data);
        }
        if (usersRes.success && usersRes.data) {
          setRecentUsers(usersRes.data.content);
        }
      } catch {
        /* fail silently for dashboard */
      } finally {
        setLoadingData(false);
      }
    };

    load();
  }, [isAuthenticated, user?.role]);

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-background-secondary">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  if (!isAuthenticated) return null;

  if (user?.role !== "ADMIN") {
    return (
      <div className="flex h-screen flex-col items-center justify-center gap-4 bg-background-secondary">
        <ShieldCheck className="h-16 w-16 text-red-400" />
        <h1 className="text-2xl font-display font-bold text-foreground">
          Access Denied
        </h1>
        <p className="text-foreground-secondary">
          You do not have permission to view this page.
        </p>
        <Link href="/">
          <Button variant="outline">Back to Home</Button>
        </Link>
      </div>
    );
  }

  /* ---- Build stat cards from analytics ---- */
  const stats = analytics
    ? [
        {
          label: "Total Users",
          value: analytics.totalUsers.toLocaleString(),
          change: `+${analytics.newUsersLast30Days} this month`,
          trend: "up" as const,
          icon: Users,
          color: "text-blue-400",
          bg: "bg-blue-500/10",
        },
        {
          label: "Total Projects",
          value: analytics.totalProjects.toLocaleString(),
          change: `+${analytics.newProjectsLast30Days} this month`,
          trend: "up" as const,
          icon: FolderKanban,
          color: "text-violet-400",
          bg: "bg-violet-500/10",
        },
        {
          label: "Documents Generated",
          value: analytics.totalDocuments.toLocaleString(),
          change: `${analytics.completedDocuments} completed`,
          trend: "up" as const,
          icon: FileText,
          color: "text-emerald-400",
          bg: "bg-emerald-500/10",
        },
        {
          label: "Active (7d)",
          value: analytics.activeUsersLast7Days.toLocaleString(),
          change: `${analytics.totalAuditEvents.toLocaleString()} events`,
          trend:
            analytics.activeUsersLast7Days > 0
              ? ("up" as const)
              : ("down" as const),
          icon: Activity,
          color: "text-amber-400",
          bg: "bg-amber-500/10",
        },
      ]
    : [];

  /* ---- Project type distribution from analytics ---- */
  const projectTypes = analytics
    ? Object.entries(analytics.projectsByType)
        .sort((a, b) => b[1] - a[1])
        .map(([type, count]) => ({
          label: formatType(type),
          count,
          percent:
            analytics.totalProjects > 0
              ? Math.round((count / analytics.totalProjects) * 100)
              : 0,
        }))
    : [];

  const typeColors = [
    "bg-blue-500",
    "bg-violet-500",
    "bg-emerald-500",
    "bg-amber-500",
    "bg-rose-500",
    "bg-cyan-500",
  ];

  return (
    <PageTransition>
      <main className="min-h-screen bg-background-secondary">
        <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
          {/* ---------- Header ---------- */}
          <FadeIn>
            <div className="mb-10 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-center gap-3">
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10">
                  <Shield className="h-6 w-6 text-primary" />
                </div>
                <div>
                  <h1 className="text-3xl font-display font-bold gradient-text">
                    Admin Panel
                  </h1>
                  <p className="text-sm text-foreground-secondary">
                    Platform analytics &amp; overview
                  </p>
                </div>
                <span className="ml-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary border border-primary/20">
                  ADMIN
                </span>
              </div>

              <div className="flex items-center gap-2 text-sm text-foreground-secondary">
                <Clock className="h-4 w-4" />
                <span>
                  Last updated:{" "}
                  {new Date().toLocaleString("en-US", {
                    month: "short",
                    day: "numeric",
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </span>
              </div>
            </div>
          </FadeIn>

          {/* ---------- Stats Overview ---------- */}
          {loadingData ? (
            <div className="flex items-center justify-center py-20">
              <Loader2 className="h-6 w-6 animate-spin text-primary" />
            </div>
          ) : (
            <>
              <StaggerContainer className="mb-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {stats.map((stat) => (
                  <StaggerItem key={stat.label}>
                    <Card hover className="relative overflow-hidden">
                      <div className="flex items-start justify-between">
                        <div>
                          <p className="text-sm text-foreground-secondary">
                            {stat.label}
                          </p>
                          <p className="mt-1 text-3xl font-display font-bold text-foreground">
                            {stat.value}
                          </p>
                          <div className="mt-2 flex items-center gap-1 text-sm">
                            {stat.trend === "up" ? (
                              <ArrowUpRight className="h-4 w-4 text-emerald-400" />
                            ) : (
                              <ArrowDownRight className="h-4 w-4 text-red-400" />
                            )}
                            <span
                              className={
                                stat.trend === "up"
                                  ? "text-emerald-400"
                                  : "text-red-400"
                              }
                            >
                              {stat.change}
                            </span>
                          </div>
                        </div>
                        <div
                          className={`flex h-10 w-10 items-center justify-center rounded-xl ${stat.bg}`}
                        >
                          <stat.icon className={`h-5 w-5 ${stat.color}`} />
                        </div>
                      </div>
                    </Card>
                  </StaggerItem>
                ))}
              </StaggerContainer>

              {/* ---------- Project Type Distribution ---------- */}
              {projectTypes.length > 0 && (
                <FadeIn delay={0.1} className="mb-8">
                  <Card>
                    <div className="mb-6">
                      <h2 className="text-lg font-display font-semibold text-foreground">
                        Project Type Distribution
                      </h2>
                      <p className="text-sm text-foreground-secondary">
                        {analytics!.totalProjects.toLocaleString()} total projects
                      </p>
                    </div>

                    <div className="space-y-4">
                      {projectTypes.map((type, i) => (
                        <div key={type.label} className="space-y-1.5">
                          <div className="flex items-center justify-between text-sm">
                            <span className="text-foreground">{type.label}</span>
                            <span className="font-medium text-foreground-secondary">
                              {type.count} ({type.percent}%)
                            </span>
                          </div>
                          <div className="h-2.5 w-full overflow-hidden rounded-full bg-background-secondary">
                            <motion.div
                              className={`h-full rounded-full ${typeColors[i % typeColors.length]}`}
                              initial={{ width: 0 }}
                              whileInView={{ width: `${type.percent}%` }}
                              viewport={{ once: true }}
                              transition={{
                                duration: 0.8,
                                delay: i * 0.08,
                                ease: [0.22, 1, 0.36, 1],
                              }}
                            />
                          </div>
                        </div>
                      ))}
                    </div>
                  </Card>
                </FadeIn>
              )}

              {/* ---------- Recent Registrations ---------- */}
              <FadeIn delay={0.15} className="mb-8">
                <Card>
                  <div className="mb-6 flex items-center justify-between">
                    <div>
                      <h2 className="text-lg font-display font-semibold text-foreground">
                        Recent Registrations
                      </h2>
                      <p className="text-sm text-foreground-secondary">
                        Latest user sign-ups
                      </p>
                    </div>
                    <Link href="/admin/users">
                      <Button
                        variant="ghost"
                        size="sm"
                        icon={<ChevronRight className="h-4 w-4" />}
                      >
                        View All
                      </Button>
                    </Link>
                  </div>

                  {recentUsers.length === 0 ? (
                    <p className="text-sm text-foreground-secondary text-center py-8">
                      No users yet
                    </p>
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="w-full text-sm">
                        <thead>
                          <tr className="border-b border-border text-left text-foreground-secondary">
                            <th className="pb-3 pr-4 font-medium">Name</th>
                            <th className="pb-3 pr-4 font-medium">Email</th>
                            <th className="pb-3 pr-4 font-medium">Joined</th>
                            <th className="pb-3 font-medium">Status</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-border">
                          {recentUsers.map((u) => (
                            <tr key={u.id} className="group">
                              <td className="py-3 pr-4">
                                <div className="flex items-center gap-3">
                                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
                                    {getInitials(u.fullName)}
                                  </div>
                                  <span className="font-medium text-foreground">
                                    {u.fullName}
                                  </span>
                                </div>
                              </td>
                              <td className="py-3 pr-4 text-foreground-secondary">
                                <div className="flex items-center gap-1.5">
                                  <Mail className="h-3.5 w-3.5" />
                                  {u.email}
                                </div>
                              </td>
                              <td className="py-3 pr-4 text-foreground-secondary">
                                {formatRelativeTime(u.createdAt)}
                              </td>
                              <td className="py-3">
                                <span
                                  className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ${
                                    u.active
                                      ? "bg-emerald-500/10 text-emerald-400"
                                      : "bg-red-500/10 text-red-400"
                                  }`}
                                >
                                  <Circle className="h-1.5 w-1.5 fill-current" />
                                  {u.active ? "Active" : "Suspended"}
                                </span>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </Card>
              </FadeIn>

              {/* ---------- Platform Health & Quick Actions ---------- */}
              <div className="grid gap-6 lg:grid-cols-2">
                {/* Platform Health */}
                <FadeIn delay={0.2}>
                  <Card>
                    <div className="mb-6">
                      <h2 className="text-lg font-display font-semibold text-foreground">
                        Platform Health
                      </h2>
                      <p className="text-sm text-foreground-secondary">
                        Service status overview
                      </p>
                    </div>

                    <div className="space-y-4">
                      {platformHealth.map((service) => (
                        <div
                          key={service.service}
                          className="flex items-center justify-between rounded-xl border border-border bg-background-secondary p-4"
                        >
                          <div className="flex items-center gap-3">
                            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-card">
                              <service.icon className="h-5 w-5 text-foreground-secondary" />
                            </div>
                            <div>
                              <p className="font-medium text-foreground">
                                {service.service}
                              </p>
                              <p className="text-xs text-foreground-secondary">
                                Uptime: {service.uptime}
                              </p>
                            </div>
                          </div>
                          <div className="flex items-center gap-2">
                            {service.status === "degraded" && (
                              <AlertTriangle className="h-4 w-4 text-amber-400" />
                            )}
                            <span
                              className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium ${
                                statusBg[service.status]
                              } ${statusColors[service.status]}`}
                            >
                              <Circle className="h-1.5 w-1.5 fill-current" />
                              {statusLabel[service.status]}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </Card>
                </FadeIn>

                {/* Quick Actions */}
                <FadeIn delay={0.25}>
                  <Card className="flex h-full flex-col">
                    <div className="mb-6">
                      <h2 className="text-lg font-display font-semibold text-foreground">
                        Quick Actions
                      </h2>
                      <p className="text-sm text-foreground-secondary">
                        Common admin operations
                      </p>
                    </div>

                    <div className="flex flex-1 flex-col gap-3">
                      <Link href="/admin/users" className="block">
                        <motion.div
                          whileHover={{ scale: 1.01 }}
                          whileTap={{ scale: 0.99 }}
                          className="flex items-center justify-between rounded-xl border border-border bg-background-secondary p-4 cursor-pointer hover:border-primary/30 transition-colors"
                        >
                          <div className="flex items-center gap-3">
                            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-500/10">
                              <Users className="h-5 w-5 text-blue-400" />
                            </div>
                            <div>
                              <p className="font-medium text-foreground">
                                View All Users
                              </p>
                              <p className="text-xs text-foreground-secondary">
                                Manage user accounts &amp; roles
                              </p>
                            </div>
                          </div>
                          <ChevronRight className="h-5 w-5 text-foreground-secondary" />
                        </motion.div>
                      </Link>

                      <motion.div
                        whileHover={{ scale: 1.01 }}
                        whileTap={{ scale: 0.99 }}
                        className="flex items-center justify-between rounded-xl border border-border bg-background-secondary p-4 cursor-pointer hover:border-primary/30 transition-colors"
                        onClick={() => {}}
                      >
                        <div className="flex items-center gap-3">
                          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-500/10">
                            <Download className="h-5 w-5 text-emerald-400" />
                          </div>
                          <div>
                            <p className="font-medium text-foreground">
                              Export Report
                            </p>
                            <p className="text-xs text-foreground-secondary">
                              Download analytics as CSV
                            </p>
                          </div>
                        </div>
                        <ChevronRight className="h-5 w-5 text-foreground-secondary" />
                      </motion.div>

                      <motion.div
                        whileHover={{ scale: 1.01 }}
                        whileTap={{ scale: 0.99 }}
                        className="flex items-center justify-between rounded-xl border border-border bg-background-secondary p-4 cursor-pointer hover:border-primary/30 transition-colors"
                        onClick={() => {}}
                      >
                        <div className="flex items-center gap-3">
                          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-violet-500/10">
                            <ScrollText className="h-5 w-5 text-violet-400" />
                          </div>
                          <div>
                            <p className="font-medium text-foreground">
                              System Logs
                            </p>
                            <p className="text-xs text-foreground-secondary">
                              View server &amp; application logs
                            </p>
                          </div>
                        </div>
                        <ChevronRight className="h-5 w-5 text-foreground-secondary" />
                      </motion.div>
                    </div>
                  </Card>
                </FadeIn>
              </div>
            </>
          )}
        </div>
      </main>
    </PageTransition>
  );
}
