"use client";

import { useState, useEffect } from "react";
import { motion } from "framer-motion";
import {
  Users,
  FolderKanban,
  FileText,
  Activity,
  ArrowUpRight,
  ArrowDownRight,
  Shield,
  ChevronRight,
  Circle,
  Clock,
  Mail,
  Loader2,
  ShieldCheck,
} from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import {
  adminApi,
  type AuditLogResponse,
  type AdminAnalyticsResponse,
  type AdminUserResponse,
} from "@/lib/api";
import { formatRelativeTime, getInitials } from "@/lib/utils";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import AdminAuditTimeline from "@/components/admin/AdminAuditTimeline";
import {
  FadeIn,
  StaggerContainer,
  StaggerItem,
  PageTransition,
} from "@/components/ui/Animations";

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
  const { user, isAuthenticated, isLoading } = useAuthStore();

  const [analytics, setAnalytics] = useState<AdminAnalyticsResponse | null>(null);
  const [recentUsers, setRecentUsers] = useState<AdminUserResponse[]>([]);
  const [auditEvents, setAuditEvents] = useState<AuditLogResponse[]>([]);
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
        const [analyticsRes, usersRes, auditRes] = await Promise.all([
          adminApi.analytics(),
          adminApi.listUsers({ page: 0, size: 5 }),
          adminApi.auditLogs({ page: 0, size: 6 }),
        ]);

        if (analyticsRes.success && analyticsRes.data) {
          setAnalytics(analyticsRes.data);
        }
        if (usersRes.success && usersRes.data) {
          setRecentUsers(usersRes.data.content);
        }
        if (auditRes.success && auditRes.data) {
          setAuditEvents(auditRes.data.content);
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
        <Loader2 className="h-8 w-8 animate-spin text-accent" />
      </div>
    );
  }

  if (!isAuthenticated) return null;

  if (user?.role !== "ADMIN") {
    return (
      <div className="flex h-screen flex-col items-center justify-center gap-4 bg-background-secondary">
        <ShieldCheck className="h-16 w-16 text-error" />
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
          color: "text-info",
          bg: "bg-info/10",
        },
        {
          label: "Total Projects",
          value: analytics.totalProjects.toLocaleString(),
          change: `+${analytics.newProjectsLast30Days} this month`,
          trend: "up" as const,
          icon: FolderKanban,
          color: "text-accent",
          bg: "bg-accent-light",
        },
        {
          label: "Documents Generated",
          value: analytics.totalDocuments.toLocaleString(),
          change: `${analytics.completedDocuments} completed`,
          trend: "up" as const,
          icon: FileText,
          color: "text-success",
          bg: "bg-success/10",
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
          color: "text-warning",
          bg: "bg-warning/10",
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
    "bg-accent",
    "bg-info",
    "bg-success",
    "bg-warning",
    "bg-error",
    "bg-accent",
  ];

  return (
    <PageTransition>
      <section className="workspace-page">
        <div className="workspace-page__inner max-w-7xl">
          {/* ---------- Header ---------- */}
          <FadeIn>
            <div className="mb-10 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-center gap-3">
                <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-accent-light">
                  <Shield className="h-6 w-6 text-accent" />
                </div>
                <div>
                  <p className="workspace-page__eyebrow">System operations</p>
                  <h1 className="workspace-page__title text-3xl">
                    Admin Panel
                  </h1>
                  <p className="text-sm text-foreground-secondary">
                    Platform analytics &amp; overview
                  </p>
                </div>
                <span className="status-badge status-badge--ready ml-2">
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
              <Loader2 className="h-6 w-6 animate-spin text-accent" />
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
                              <ArrowUpRight className="h-4 w-4 text-success" />
                            ) : (
                              <ArrowDownRight className="h-4 w-4 text-error" />
                            )}
                            <span
                              className={
                                stat.trend === "up"
                                  ? "text-success"
                                  : "text-error"
                              }
                            >
                              {stat.change}
                            </span>
                          </div>
                        </div>
                        <div
                          className={`flex h-10 w-10 items-center justify-center rounded-lg ${stat.bg}`}
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
                          <div className="h-2.5 w-full overflow-hidden rounded bg-background-secondary">
                            <motion.div
                              className={`h-full rounded ${typeColors[i % typeColors.length]}`}
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

              <FadeIn delay={0.12} className="mb-8">
                <AdminAuditTimeline events={auditEvents} />
              </FadeIn>

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
                                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-accent-light text-sm font-semibold text-accent">
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
                                  className={`inline-flex items-center gap-1.5 rounded border px-2.5 py-0.5 text-xs font-medium ${
                                    u.active
                                      ? "border-success/30 bg-success/10 text-success"
                                      : "border-error/30 bg-error/10 text-error"
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

              <FadeIn delay={0.2}>
                <Card>
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <h2 className="text-lg font-display font-semibold text-foreground">Administration</h2>
                      <p className="mt-1 text-sm text-foreground-secondary">Manage user accounts and roles. Live platform-health, log-viewing, and report-export tools are not available yet.</p>
                    </div>
                    <Link href="/admin/users"><Button icon={<Users className="h-4 w-4" />}>Manage Users</Button></Link>
                  </div>
                </Card>
              </FadeIn>
            </>
          )}
        </div>
      </section>
    </PageTransition>
  );
}
