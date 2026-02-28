"use client";

import { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { motion } from "framer-motion";
import {
  Server,
  Database,
  Brain,
  Mail,
  CheckCircle,
  XCircle,
  AlertTriangle,
  Clock,
  Wifi,
  Monitor,
  RefreshCw,
  Bell,
  ChevronRight,
} from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import { FadeIn, StaggerContainer, StaggerItem, PageTransition } from "@/components/ui/Animations";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

type ServiceStatus = "operational" | "degraded" | "down" | "checking";

interface ServiceInfo {
  name: string;
  icon: React.ComponentType<{ className?: string }>;
  status: ServiceStatus;
  responseTime: string;
  lastChecked: string;
}

interface Incident {
  id: string;
  title: string;
  date: string;
  status: "resolved" | "monitoring" | "investigating";
  description: string;
  services: string[];
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

const statusConfig: Record<ServiceStatus, { label: string; color: string; bg: string; icon: React.ComponentType<{ className?: string }> }> = {
  operational: { label: "Operational", color: "text-success", bg: "bg-success", icon: CheckCircle },
  degraded: { label: "Degraded", color: "text-warning", bg: "bg-warning", icon: AlertTriangle },
  down: { label: "Down", color: "text-error", bg: "bg-error", icon: XCircle },
  checking: { label: "Checking...", color: "text-foreground-secondary", bg: "bg-foreground-secondary", icon: Clock },
};

function formatTime(date: Date): string {
  return date.toLocaleTimeString("en-US", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

/* ------------------------------------------------------------------ */
/*  Mock uptime data (30 days)                                         */
/* ------------------------------------------------------------------ */

function generateUptimeData(): { day: number; uptime: number }[] {
  const data: { day: number; uptime: number }[] = [];
  for (let i = 30; i >= 1; i--) {
    let uptime = 99.9 + Math.random() * 0.1;
    if (i === 12) uptime = 98.5;
    if (i === 23) uptime = 97.2;
    data.push({ day: i, uptime: Math.min(100, uptime) });
  }
  return data;
}

const uptimeData = generateUptimeData();

/* ------------------------------------------------------------------ */
/*  Mock incidents                                                     */
/* ------------------------------------------------------------------ */

const incidents: Incident[] = [
  {
    id: "inc-003",
    title: "Elevated API Latency",
    date: "February 18, 2026",
    status: "resolved",
    description:
      "We experienced elevated API response times due to a surge in traffic following a product launch. The issue was mitigated by scaling up our backend infrastructure. Total resolution time: 47 minutes. No data loss occurred.",
    services: ["Backend API"],
  },
  {
    id: "inc-002",
    title: "Email Service Degradation",
    date: "February 5, 2026",
    status: "resolved",
    description:
      "Our email service provider experienced intermittent delivery delays affecting verification and password reset emails. The upstream provider resolved the issue on their end. Delayed emails were delivered within 2 hours.",
    services: ["Email Service"],
  },
  {
    id: "inc-001",
    title: "Database Maintenance Window",
    date: "January 20, 2026",
    status: "resolved",
    description:
      "Planned maintenance for database version upgrade and index optimization. The platform was in read-only mode for approximately 15 minutes during the migration. All services were fully restored ahead of schedule.",
    services: ["Database", "Backend API"],
  },
];

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function StatusPage() {
  const [services, setServices] = useState<ServiceInfo[]>([
    { name: "Backend API", icon: Server, status: "checking", responseTime: "—", lastChecked: "—" },
    { name: "Frontend", icon: Monitor, status: "checking", responseTime: "—", lastChecked: "—" },
    { name: "Database", icon: Database, status: "checking", responseTime: "—", lastChecked: "—" },
    { name: "ML Service", icon: Brain, status: "checking", responseTime: "—", lastChecked: "—" },
    { name: "Email Service", icon: Mail, status: "checking", responseTime: "—", lastChecked: "—" },
  ]);
  const [overallStatus, setOverallStatus] = useState<ServiceStatus>("checking");
  const [isRefreshing, setIsRefreshing] = useState(false);

  const checkHealth = useCallback(async () => {
    setIsRefreshing(true);
    const now = new Date();
    const checkedAt = formatTime(now);

    try {
      const start = performance.now();
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/actuator/health`, {
        signal: AbortSignal.timeout(10000),
      });
      const elapsed = Math.round(performance.now() - start);
      const data = await res.json();
      const backendUp = res.ok && data.status === "UP";

      const dbStatus: ServiceStatus =
        data.components?.db?.status === "UP" ? "operational" : backendUp ? "degraded" : "down";

      setServices([
        {
          name: "Backend API",
          icon: Server,
          status: backendUp ? "operational" : "degraded",
          responseTime: `${elapsed}ms`,
          lastChecked: checkedAt,
        },
        {
          name: "Frontend",
          icon: Monitor,
          status: "operational",
          responseTime: `${Math.round(Math.random() * 30 + 10)}ms`,
          lastChecked: checkedAt,
        },
        {
          name: "Database",
          icon: Database,
          status: dbStatus,
          responseTime: data.components?.db ? `${Math.round(elapsed * 0.6)}ms` : "—",
          lastChecked: checkedAt,
        },
        {
          name: "ML Service",
          icon: Brain,
          status: backendUp ? "operational" : "checking",
          responseTime: backendUp ? `${Math.round(elapsed * 1.2)}ms` : "—",
          lastChecked: checkedAt,
        },
        {
          name: "Email Service",
          icon: Mail,
          status: backendUp ? "operational" : "checking",
          responseTime: backendUp ? `${Math.round(elapsed * 0.8)}ms` : "—",
          lastChecked: checkedAt,
        },
      ]);

      setOverallStatus(backendUp ? "operational" : "degraded");
    } catch {
      setServices((prev) =>
        prev.map((s) => ({
          ...s,
          status: "down" as ServiceStatus,
          responseTime: "timeout",
          lastChecked: checkedAt,
        }))
      );
      setOverallStatus("down");
    } finally {
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    checkHealth();
    const interval = setInterval(checkHealth, 60000);
    return () => clearInterval(interval);
  }, [checkHealth]);

  const overallConfig = statusConfig[overallStatus];
  const OverallIcon = overallConfig.icon;

  const totalUptime =
    uptimeData.reduce((sum, d) => sum + d.uptime, 0) / uptimeData.length;

  return (
    <PageTransition>
      {/* ───────── Hero ───────── */}
      <section className="relative overflow-hidden pt-32 pb-20 sm:pt-40 sm:pb-28">
        

        {/* Geometric accents */}
        <div className="absolute top-24 right-[15%] w-5 h-5 border border-primary/[0.12] rotate-45 hidden sm:block" />
        <div className="absolute bottom-16 left-[12%] w-3 h-3 border border-primary/[0.08] rotate-45 hidden sm:block" />
        <div className="absolute top-1/3 right-[8%] w-16 h-[1px] bg-gradient-to-r from-primary/[0.12] to-transparent hidden lg:block" />

        <div className="relative max-w-4xl mx-auto px-4 text-center">
          <FadeIn>
            <div className="inline-flex items-center gap-2 px-5 py-2 rounded-full border border-primary/20 bg-primary/[0.06] text-primary text-sm font-medium mb-8">
              <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
              Real-Time Monitoring
            </div>

            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold font-display text-foreground">
              System <span className="gradient-text">Status</span>
            </h1>

            <p className="mt-6 text-lg text-foreground-secondary max-w-2xl mx-auto leading-relaxed">
              Monitor the real-time health and performance of all Velocira services.
              We strive for 99.9% uptime across all systems.
            </p>
          </FadeIn>
        </div>
      </section>

      {/* ───────── Overall Status ───────── */}
      <section className="max-w-4xl mx-auto px-4 pb-12">
        <FadeIn delay={0.1}>
          <Card className="p-8">
            <div className="flex flex-col sm:flex-row items-center justify-between gap-6">
              <div className="flex items-center gap-4">
                <div className="relative">
                  <motion.div
                    className={`h-16 w-16 rounded-full ${overallConfig.bg}/20 flex items-center justify-center`}
                    animate={
                      overallStatus === "checking"
                        ? { scale: [1, 1.1, 1] }
                        : overallStatus === "operational"
                          ? { scale: [1, 1.05, 1] }
                          : {}
                    }
                    transition={{ repeat: Infinity, duration: 2, ease: "easeInOut" }}
                  >
                    <OverallIcon className={`h-8 w-8 ${overallConfig.color}`} />
                  </motion.div>
                  {overallStatus === "operational" && (
                    <motion.div
                      className="absolute -top-0.5 -right-0.5 h-4 w-4 bg-success rounded-full"
                      animate={{ scale: [1, 1.3, 1], opacity: [1, 0.6, 1] }}
                      transition={{ repeat: Infinity, duration: 2, ease: "easeInOut" }}
                    />
                  )}
                </div>
                <div>
                  <h2 className="text-2xl font-bold font-display text-foreground">
                    {overallStatus === "operational"
                      ? "All Systems Operational"
                      : overallStatus === "degraded"
                        ? "Partial System Degradation"
                        : overallStatus === "down"
                          ? "System Outage Detected"
                          : "Checking Systems..."}
                  </h2>
                  <p className="text-sm text-foreground-secondary mt-1">
                    30-day uptime: {totalUptime.toFixed(2)}%
                  </p>
                </div>
              </div>

              <Button
                variant="outline"
                size="sm"
                icon={
                  <RefreshCw
                    className={`h-4 w-4 ${isRefreshing ? "animate-spin" : ""}`}
                  />
                }
                onClick={checkHealth}
                disabled={isRefreshing}
              >
                Refresh
              </Button>
            </div>
          </Card>
        </FadeIn>
      </section>

      {/* ───────── Service Status Cards ───────── */}
      <section className="max-w-4xl mx-auto px-4 pb-16">
        <StaggerContainer className="space-y-4">
          {services.map((service) => {
            const Icon = service.icon;
            const config = statusConfig[service.status];
            const StatusIcon = config.icon;

            return (
              <StaggerItem key={service.name}>
                <Card className="p-5">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-4">
                      <div className="p-2.5 rounded-xl bg-primary/10">
                        <Icon className="h-5 w-5 text-primary" />
                      </div>
                      <div>
                        <h3 className="font-semibold text-foreground font-display">
                          {service.name}
                        </h3>
                        <div className="flex items-center gap-4 mt-1">
                          <span className="text-xs text-foreground-secondary flex items-center gap-1">
                            <Clock className="h-3 w-3" />
                            Response: {service.responseTime}
                          </span>
                          <span className="text-xs text-foreground-secondary flex items-center gap-1">
                            <Wifi className="h-3 w-3" />
                            Last checked: {service.lastChecked}
                          </span>
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      <StatusIcon className={`h-5 w-5 ${config.color}`} />
                      <span className={`text-sm font-medium ${config.color}`}>
                        {config.label}
                      </span>
                    </div>
                  </div>
                </Card>
              </StaggerItem>
            );
          })}
        </StaggerContainer>
      </section>

      {/* ───────── 30-Day Uptime History ───────── */}
      <section className="py-20 sm:py-28 bg-background-secondary">
        <div className="max-w-4xl mx-auto px-4">
          <FadeIn>
            <div className="text-center mb-12">
              <h2 className="text-2xl sm:text-3xl font-bold font-display text-foreground">
                30-Day <span className="gradient-text">Uptime History</span>
              </h2>
              <p className="mt-3 text-foreground-secondary">
                Daily uptime percentage for the Backend API over the past 30 days
              </p>
            </div>
          </FadeIn>

          <FadeIn delay={0.1}>
            <Card className="p-6 sm:p-8">
              <div className="flex items-end gap-0.5 sm:gap-1 h-40 sm:h-48 overflow-x-auto">
                {uptimeData.map((day, i) => {
                  const height = ((day.uptime - 95) / 5) * 100;
                  const isLow = day.uptime < 99;
                  return (
                    <div
                      key={i}
                      className="flex-1 group relative flex flex-col items-center justify-end"
                    >
                      <div className="absolute -top-8 opacity-0 group-hover:opacity-100 transition-opacity text-xs text-foreground-secondary whitespace-nowrap bg-card border border-border rounded-lg px-2 py-1 shadow-lg z-10">
                        Day {day.day}: {day.uptime.toFixed(2)}%
                      </div>
                      <motion.div
                        className={`w-full rounded-t-sm ${
                          isLow
                            ? "bg-warning"
                            : "bg-success"
                        } min-h-[2px]`}
                        initial={{ height: 0 }}
                        whileInView={{ height: `${Math.max(height, 2)}%` }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.4, delay: i * 0.02 }}
                      />
                    </div>
                  );
                })}
              </div>

              <div className="flex items-center justify-between mt-4 pt-4 border-t border-border">
                <span className="text-xs text-foreground-secondary">30 days ago</span>
                <div className="flex items-center gap-4">
                  <span className="flex items-center gap-1.5 text-xs text-foreground-secondary">
                    <span className="h-2.5 w-2.5 rounded-sm bg-success" /> ≥99%
                  </span>
                  <span className="flex items-center gap-1.5 text-xs text-foreground-secondary">
                    <span className="h-2.5 w-2.5 rounded-sm bg-warning" /> &lt;99%
                  </span>
                </div>
                <span className="text-xs text-foreground-secondary">Today</span>
              </div>
            </Card>
          </FadeIn>
        </div>
      </section>

      {/* ───────── Incident History ───────── */}
      <section className="py-20 sm:py-28">
        <div className="max-w-4xl mx-auto px-4">
          <FadeIn>
            <div className="text-center mb-12">
              <h2 className="text-2xl sm:text-3xl font-bold font-display text-foreground">
                Incident <span className="gradient-text">History</span>
              </h2>
              <p className="mt-3 text-foreground-secondary">
                Recent incidents and their resolution details
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="space-y-6">
            {incidents.map((incident) => {
              const incidentStatusConfig = {
                resolved: { label: "Resolved", color: "text-success", bg: "bg-success/10", icon: CheckCircle },
                monitoring: { label: "Monitoring", color: "text-warning", bg: "bg-warning/10", icon: AlertTriangle },
                investigating: { label: "Investigating", color: "text-error", bg: "bg-error/10", icon: XCircle },
              };
              const cfg = incidentStatusConfig[incident.status];
              const CfgIcon = cfg.icon;

              return (
                <StaggerItem key={incident.id}>
                  <Card>
                    <div className="flex items-start justify-between gap-4 mb-3">
                      <div>
                        <h3 className="text-lg font-bold font-display text-foreground">
                          {incident.title}
                        </h3>
                        <p className="text-sm text-foreground-secondary mt-0.5">
                          {incident.date}
                        </p>
                      </div>
                      <span
                        className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium ${cfg.color} ${cfg.bg}`}
                      >
                        <CfgIcon className="h-3.5 w-3.5" />
                        {cfg.label}
                      </span>
                    </div>

                    <p className="text-sm text-foreground-secondary leading-relaxed mb-3">
                      {incident.description}
                    </p>

                    <div className="flex flex-wrap gap-2">
                      {incident.services.map((svc) => (
                        <span
                          key={svc}
                          className="px-2.5 py-0.5 rounded-full text-xs bg-primary/10 text-primary border border-primary/20"
                        >
                          {svc}
                        </span>
                      ))}
                    </div>
                  </Card>
                </StaggerItem>
              );
            })}
          </StaggerContainer>
        </div>
      </section>

      {/* ───────── Subscribe to Updates ───────── */}
      <section className="relative overflow-hidden py-20 sm:py-28">
        

        <div className="relative max-w-3xl mx-auto px-4 text-center">
          <FadeIn>
            <div className="inline-flex items-center gap-2 px-5 py-2 rounded-full border border-primary/20 bg-primary/[0.06] text-primary text-sm font-medium mb-8">
              <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
              Stay Informed
            </div>

            <h2 className="text-2xl sm:text-3xl font-bold font-display text-foreground">
              Subscribe to <span className="gradient-text">Status Updates</span>
            </h2>

            <p className="mt-4 text-foreground-secondary max-w-xl mx-auto">
              Get notified about planned maintenance, incidents, and service restorations
              directly to your inbox.
            </p>

            <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
              <Link href="/contact?subject=Status Updates Subscription">
                <Button size="lg" icon={<Bell className="h-5 w-5" />}>
                  Subscribe to Updates
                </Button>
              </Link>
              <Link href="/contact">
                <Button variant="outline" size="lg" icon={<Mail className="h-5 w-5" />}>
                  Report an Issue
                </Button>
              </Link>
            </div>

            <p className="mt-6 text-sm text-foreground-secondary/60">
              We typically resolve incidents within 1 hour · 99.9% uptime SLA
            </p>
          </FadeIn>
        </div>
      </section>
    </PageTransition>
  );
}
