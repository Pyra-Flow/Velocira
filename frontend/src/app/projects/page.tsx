"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import {
  Plus,
  Search,
  LayoutGrid,
  LayoutList,
  FileText,
  Clock,
  CheckCircle2,
  AlertTriangle,
  Loader2,
  PenLine,
  ChevronRight,
  FolderOpen,
  Sparkles,
  Globe,
  Smartphone,
  Cpu,
  Server,
  Briefcase,
  Monitor,
  ChevronLeft,
} from "lucide-react";
import { useLocale } from "@/providers/LocaleProvider";
import { useAuthStore } from "@/store/authStore";
import { useProjectStore } from "@/store/projectStore";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import {
  FadeIn,
  StaggerContainer,
  StaggerItem,
  PageTransition,
} from "@/components/ui/Animations";
import { formatRelativeTime } from "@/lib/utils";
import type { ProjectResponse, ProjectStatus, ProjectType } from "@/lib/api";

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

const STATUS_CONFIG: Record<
  ProjectStatus,
  { color: string; bg: string; icon: React.ReactNode; label: string }
> = {
  DRAFT: {
    color: "text-foreground-secondary",
    bg: "bg-background-secondary",
    icon: <PenLine className="h-3 w-3" />,
    label: "Draft",
  },
  GENERATING: {
    color: "text-info",
    bg: "bg-info/10",
    icon: <Loader2 className="h-3 w-3 animate-spin" />,
    label: "Generating",
  },
  COMPLETE: {
    color: "text-success",
    bg: "bg-success/10",
    icon: <CheckCircle2 className="h-3 w-3" />,
    label: "Complete",
  },
  FAILED: {
    color: "text-error",
    bg: "bg-error/10",
    icon: <AlertTriangle className="h-3 w-3" />,
    label: "Failed",
  },
};

const TYPE_ICON: Record<ProjectType, React.ReactNode> = {
  WEB_APP: <Globe className="h-3.5 w-3.5" />,
  AI_SYSTEM: <Sparkles className="h-3.5 w-3.5" />,
  MOBILE_APP: <Smartphone className="h-3.5 w-3.5" />,
  IOT: <Cpu className="h-3.5 w-3.5" />,
  API_BACKEND: <Server className="h-3.5 w-3.5" />,
  DESKTOP_APP: <Monitor className="h-3.5 w-3.5" />,
};

const TYPE_LABEL: Record<ProjectType, string> = {
  WEB_APP: "Web App",
  MOBILE_APP: "Mobile App",
  AI_SYSTEM: "AI System",
  IOT: "IoT",
  DESKTOP_APP: "Desktop App",
  API_BACKEND: "API/Backend",
};

const FILTER_TABS: { label: string; value: ProjectStatus | "ALL" }[] = [
  { label: "All", value: "ALL" },
  { label: "Draft", value: "DRAFT" },
  { label: "Generating", value: "GENERATING" },
  { label: "Complete", value: "COMPLETE" },
  { label: "Failed", value: "FAILED" },
];

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function ProjectsPage() {
  const { t } = useLocale();
  const { isAuthenticated, isLoading: authLoading } = useAuthStore();
  const router = useRouter();

  const {
    projects,
    totalElements,
    totalPages,
    currentPage,
    isLoading,
    fetchProjects,
  } = useProjectStore();

  const [search, setSearch] = useState("");
  const [activeFilter, setActiveFilter] = useState<ProjectStatus | "ALL">("ALL");
  const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
  const [debouncedSearch, setDebouncedSearch] = useState("");

  /* ---- Protected route ---- */
  useEffect(() => {
    if (!authLoading && !isAuthenticated) {
      router.replace("/login");
    }
  }, [authLoading, isAuthenticated, router]);

  /* ---- Debounce search ---- */
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  /* ---- Fetch projects ---- */
  const loadProjects = useCallback(
    (page = 0) => {
      fetchProjects({
        page,
        search: debouncedSearch || undefined,
        status: activeFilter === "ALL" ? undefined : activeFilter,
      });
    },
    [fetchProjects, debouncedSearch, activeFilter]
  );

  useEffect(() => {
    if (isAuthenticated) {
      loadProjects(0);
    }
  }, [isAuthenticated, loadProjects]);

  /* ---- Loading state ---- */
  if (authLoading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  if (!isAuthenticated) return null;

  return (
    <PageTransition>
      <section className="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
        {/* -------------------------------------------------------- */}
        {/*  Header                                                   */}
        {/* -------------------------------------------------------- */}
        <FadeIn>
          <div className="mb-10 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h1 className="font-display text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
                My <span className="gradient-text">Projects</span>
              </h1>
              <p className="mt-1 text-foreground-secondary">
                Manage and track all your documentation projects
              </p>
            </div>

            <Link href="/projects/new">
              <Button
                variant="primary"
                size="md"
                icon={<Plus className="h-4 w-4" />}
              >
                New Project
              </Button>
            </Link>
          </div>
        </FadeIn>

        {/* -------------------------------------------------------- */}
        {/*  Search + View Toggle                                     */}
        {/* -------------------------------------------------------- */}
        <FadeIn delay={0.05}>
          <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="relative w-full sm:max-w-sm">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-foreground-secondary" />
              <input
                type="text"
                placeholder="Search projects…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full rounded-xl border border-border bg-card py-2.5 pl-10 pr-4 text-sm text-foreground placeholder:text-foreground-secondary focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/30 transition-colors"
              />
            </div>

            <div className="flex items-center gap-1 rounded-xl border border-border bg-card p-1">
              <button
                onClick={() => setViewMode("grid")}
                className={`rounded-lg p-2 transition-colors cursor-pointer ${
                  viewMode === "grid"
                    ? "bg-primary/10 text-primary"
                    : "text-foreground-secondary hover:text-foreground"
                }`}
                aria-label="Grid view"
              >
                <LayoutGrid className="h-4 w-4" />
              </button>
              <button
                onClick={() => setViewMode("list")}
                className={`rounded-lg p-2 transition-colors cursor-pointer ${
                  viewMode === "list"
                    ? "bg-primary/10 text-primary"
                    : "text-foreground-secondary hover:text-foreground"
                }`}
                aria-label="List view"
              >
                <LayoutList className="h-4 w-4" />
              </button>
            </div>
          </div>
        </FadeIn>

        {/* -------------------------------------------------------- */}
        {/*  Filter Tabs                                              */}
        {/* -------------------------------------------------------- */}
        <FadeIn delay={0.1}>
          <div className="mb-8 flex flex-wrap gap-2">
            {FILTER_TABS.map((tab) => {
              const isActive = activeFilter === tab.value;
              return (
                <button
                  key={tab.value}
                  onClick={() => setActiveFilter(tab.value)}
                  className={`inline-flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-colors cursor-pointer ${
                    isActive
                      ? "bg-primary/10 text-primary border border-primary/30"
                      : "bg-card text-foreground-secondary border border-border hover:text-foreground hover:border-border-hover"
                  }`}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>
        </FadeIn>

        {/* -------------------------------------------------------- */}
        {/*  Loading / Content                                        */}
        {/* -------------------------------------------------------- */}
        {isLoading ? (
          <div className="flex min-h-[40vh] items-center justify-center">
            <Loader2 className="h-8 w-8 animate-spin text-primary" />
          </div>
        ) : (
          <AnimatePresence mode="wait">
            {projects.length === 0 ? (
              <motion.div
                key="empty"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                className="flex flex-col items-center justify-center rounded-2xl border border-border bg-card py-20 text-center"
              >
                <FolderOpen className="mb-4 h-10 w-10 text-foreground-secondary/50" />
                <h3 className="mb-1 text-lg font-semibold text-foreground">
                  No projects found
                </h3>
                <p className="mb-6 max-w-sm text-sm text-foreground-secondary">
                  {debouncedSearch
                    ? `No projects matching "${debouncedSearch}". Try a different search term.`
                    : "No projects yet. Create your first project to get started!"}
                </p>
                <Link href="/projects/new">
                  <Button
                    variant="secondary"
                    size="sm"
                    icon={<Plus className="h-4 w-4" />}
                  >
                    New Project
                  </Button>
                </Link>
              </motion.div>
            ) : (
              <StaggerContainer
                key={`${activeFilter}-${debouncedSearch}-${viewMode}`}
                className={
                  viewMode === "grid"
                    ? "grid gap-5 sm:grid-cols-2 lg:grid-cols-3"
                    : "flex flex-col gap-4"
                }
              >
                {projects.map((project) => (
                  <StaggerItem key={project.id}>
                    {viewMode === "grid" ? (
                      <ProjectCard project={project} />
                    ) : (
                      <ProjectListRow project={project} />
                    )}
                  </StaggerItem>
                ))}
              </StaggerContainer>
            )}
          </AnimatePresence>
        )}

        {/* -------------------------------------------------------- */}
        {/*  Pagination                                               */}
        {/* -------------------------------------------------------- */}
        {!isLoading && projects.length > 0 && totalPages > 1 && (
          <FadeIn delay={0.2}>
            <div className="mt-10 flex items-center justify-between border-t border-border pt-6">
              <p className="text-sm text-foreground-secondary">
                Page{" "}
                <span className="font-medium text-foreground">
                  {currentPage + 1}
                </span>{" "}
                of{" "}
                <span className="font-medium text-foreground">
                  {totalPages}
                </span>{" "}
                · {totalElements} projects
              </p>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  icon={<ChevronLeft className="h-4 w-4" />}
                  disabled={currentPage === 0}
                  onClick={() => loadProjects(currentPage - 1)}
                >
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  icon={<ChevronRight className="h-4 w-4" />}
                  disabled={currentPage >= totalPages - 1}
                  onClick={() => loadProjects(currentPage + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          </FadeIn>
        )}
      </section>
    </PageTransition>
  );
}

/* ------------------------------------------------------------------ */
/*  Project Card (Grid)                                                */
/* ------------------------------------------------------------------ */

function ProjectCard({ project }: { project: ProjectResponse }) {
  const statusCfg = STATUS_CONFIG[project.status];

  return (
    <Link href={`/projects/${project.id}`}>
      <Card hover className="group relative h-full cursor-pointer">
        <div className="mb-4 flex items-center justify-between">
          <span className="inline-flex items-center gap-1.5 rounded-lg bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary">
            {TYPE_ICON[project.type] ?? <Briefcase className="h-3.5 w-3.5" />}
            {TYPE_LABEL[project.type] ?? project.type}
          </span>
          <span
            className={`inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-medium ${statusCfg.color} ${statusCfg.bg}`}
          >
            {statusCfg.icon}
            {statusCfg.label}
          </span>
        </div>

        <h3 className="mb-1 text-lg font-semibold text-foreground group-hover:text-primary transition-colors">
          {project.name}
        </h3>

        <p className="mb-4 line-clamp-2 text-sm text-foreground-secondary">
          {project.description}
        </p>

        {project.status === "GENERATING" && (
          <div className="mb-4">
            <div className="mb-1 flex items-center justify-between text-xs">
              <span className="text-foreground-secondary">Generating docs…</span>
              <span className="font-medium text-info">{project.progress}%</span>
            </div>
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-background-secondary">
              <motion.div
                className="h-full rounded-full bg-info"
                initial={{ width: 0 }}
                animate={{ width: `${project.progress}%` }}
                transition={{ duration: 1, ease: "easeOut" }}
              />
            </div>
          </div>
        )}

        <div className="flex items-center justify-between border-t border-border pt-3 text-xs text-foreground-secondary">
          <span className="inline-flex items-center gap-1.5">
            <FileText className="h-3.5 w-3.5" />
            {project.documentCount}{" "}
            {project.documentCount === 1 ? "doc" : "docs"}
          </span>
          <span className="inline-flex items-center gap-1.5">
            <Clock className="h-3.5 w-3.5" />
            {formatRelativeTime(project.updatedAt)}
          </span>
        </div>

        <div className="absolute right-4 top-1/2 -translate-y-1/2 opacity-0 transition-opacity group-hover:opacity-100">
          <ChevronRight className="h-5 w-5 text-primary" />
        </div>
      </Card>
    </Link>
  );
}

/* ------------------------------------------------------------------ */
/*  List Row Variant                                                   */
/* ------------------------------------------------------------------ */

function ProjectListRow({ project }: { project: ProjectResponse }) {
  const statusCfg = STATUS_CONFIG[project.status];

  return (
    <Link href={`/projects/${project.id}`}>
      <Card hover className="group cursor-pointer">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-4 min-w-0">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
              {TYPE_ICON[project.type] ?? <Briefcase className="h-5 w-5" />}
            </div>
            <div className="min-w-0">
              <h3 className="truncate text-sm font-semibold text-foreground group-hover:text-primary transition-colors">
                {project.name}
              </h3>
              <p className="truncate text-xs text-foreground-secondary">
                {TYPE_LABEL[project.type] ?? project.type}
              </p>
            </div>
          </div>

          {project.status === "GENERATING" && (
            <div className="hidden w-32 md:block">
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-background-secondary">
                <motion.div
                  className="h-full rounded-full bg-info"
                  initial={{ width: 0 }}
                  animate={{ width: `${project.progress}%` }}
                  transition={{ duration: 1, ease: "easeOut" }}
                />
              </div>
              <p className="mt-0.5 text-[10px] text-info font-medium text-center">
                {project.progress}%
              </p>
            </div>
          )}

          <div className="flex items-center gap-4 text-xs text-foreground-secondary">
            <span className="inline-flex items-center gap-1.5">
              <FileText className="h-3.5 w-3.5" />
              {project.documentCount}
            </span>
            <span className="inline-flex items-center gap-1.5">
              <Clock className="h-3.5 w-3.5" />
              {formatRelativeTime(project.updatedAt)}
            </span>
            <span
              className={`inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-medium ${statusCfg.color} ${statusCfg.bg}`}
            >
              {statusCfg.icon}
              {statusCfg.label}
            </span>
            <ChevronRight className="h-4 w-4 text-foreground-secondary opacity-0 transition-opacity group-hover:opacity-100" />
          </div>
        </div>
      </Card>
    </Link>
  );
}
