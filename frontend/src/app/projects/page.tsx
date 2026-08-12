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
  Loader2,
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
import { useAuthStore } from "@/store/authStore";
import { useProjectStore } from "@/store/projectStore";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import StatusBadge from "@/components/ui/StatusBadge";
import SignalMeter from "@/components/ui/SignalMeter";
import {
  FadeIn,
  StaggerContainer,
  StaggerItem,
  PageTransition,
} from "@/components/ui/Animations";
import WorkspacePageHeader from "@/components/workspace/WorkspacePageHeader";
import { formatRelativeTime } from "@/lib/utils";
import type { ProjectResponse, ProjectStatus, ProjectType } from "@/lib/api";

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

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
  { label: "Discovery", value: "DISCOVERY" },
  { label: "Ready", value: "READY_FOR_GENERATION" },
  { label: "Generating", value: "GENERATING" },
  { label: "Review", value: "NEEDS_REVIEW" },
  { label: "Approved", value: "APPROVED" },
  { label: "Failed", value: "FAILED" },
  { label: "Archived", value: "ARCHIVED" },
];

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function ProjectsPage() {
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
  const [viewMode, setViewMode] = useState<"grid" | "list">("list");
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
        <Loader2 className="h-8 w-8 animate-spin text-accent" />
      </div>
    );
  }

  if (!isAuthenticated) return null;

  return (
    <PageTransition>
      <section className="workspace-page">
        <div className="workspace-page__inner">
        {/* -------------------------------------------------------- */}
        {/*  Header                                                   */}
        {/* -------------------------------------------------------- */}
        <FadeIn>
          <WorkspacePageHeader
            eyebrow="Project library"
            title="Every project, clearly staged."
            description="Search the work in motion, surface the review blockers, and open the right project briefing without hunting through a generic list."
            actions={projects.length > 0 ? <Link href="/projects/new"><Button icon={<Plus className="h-4 w-4" />}>New project</Button></Link> : undefined}
          />
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
                placeholder="Search by project name or context"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                aria-label="Search projects"
                className="w-full rounded-md border border-border bg-card py-2.5 pl-10 pr-4 text-sm text-foreground placeholder:text-foreground-secondary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-colors"
              />
            </div>

            <div className="sf-filter-row flex items-center gap-1 rounded-xl border border-border bg-card p-1">
              <button
                type="button"
                onClick={() => setViewMode("grid")}
                className={`rounded-lg p-2 transition-colors cursor-pointer ${
                  viewMode === "grid"
                    ? "bg-accent-light text-accent"
                    : "text-foreground-secondary hover:text-foreground"
                }`}
                aria-label="Grid view"
                aria-pressed={viewMode === "grid"}
              >
                <LayoutGrid className="h-4 w-4" />
              </button>
              <button
                type="button"
                onClick={() => setViewMode("list")}
                className={`rounded-lg p-2 transition-colors cursor-pointer ${
                  viewMode === "list"
                    ? "bg-accent-light text-accent"
                    : "text-foreground-secondary hover:text-foreground"
                }`}
                aria-label="List view"
                aria-pressed={viewMode === "list"}
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
          <div className="sf-filter-row mb-8 flex flex-wrap gap-2" aria-label="Project status filter">
            {FILTER_TABS.map((tab) => {
              const isActive = activeFilter === tab.value;
              return (
                <button
                  key={tab.value}
                  type="button"
                  aria-pressed={isActive}
                  onClick={() => setActiveFilter(tab.value)}
                  className={`inline-flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-colors cursor-pointer ${
                    isActive
                      ? "bg-accent-light text-accent border border-accent/30"
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
          <div className={viewMode === "grid" ? "grid gap-4 sm:grid-cols-2 lg:grid-cols-3" : "grid gap-3"} role="status" aria-live="polite" aria-label="Loading projects">
            {[0, 1, 2].map((item) => <div key={item} className={`workspace-skeleton ${viewMode === "grid" ? "h-56" : "h-24"}`} />)}
          </div>
        ) : (
          <AnimatePresence mode="wait">
            {projects.length === 0 ? (
              <motion.div key="empty" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }} className="workspace-empty-state">
                <FolderOpen aria-hidden="true" />
                <h2>{debouncedSearch ? "No matching projects" : "No projects in this stage"}</h2>
                <p>{debouncedSearch ? `Nothing matched “${debouncedSearch}”. Try a broader project name or clear the search.` : activeFilter === "ALL" ? "Create a project, describe your idea, and get a first project plan." : "Try another lifecycle filter, or open a project to continue its current review step."}</p>
                {!debouncedSearch && activeFilter === "ALL" && <Link href="/projects/new"><Button size="sm" icon={<Plus className="h-4 w-4" />}>Create project</Button></Link>}
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
        </div>
      </section>
    </PageTransition>
  );
}

/* ------------------------------------------------------------------ */
/*  Project Card (Grid)                                                */
/* ------------------------------------------------------------------ */

function ProjectCard({ project }: { project: ProjectResponse }) {
  return (
    <Link href={`/projects/${project.id}`}>
      <Card hover className="group relative h-full cursor-pointer">
        <div className="mb-4 flex items-center justify-between">
          <span className="sf-meta inline-flex items-center gap-1.5 rounded-md border border-accent/30 bg-accent-light px-2.5 py-1 text-accent">
            {TYPE_ICON[project.type] ?? <Briefcase className="h-3.5 w-3.5" />}
            {TYPE_LABEL[project.type] ?? project.type}
          </span>
          <StatusBadge status={project.status} />
        </div>

        <h3 className="mb-1 text-lg font-semibold text-foreground group-hover:text-accent transition-colors">
          {project.name}
        </h3>

        <p className="mb-4 line-clamp-2 text-sm text-foreground-secondary">
          {project.description}
        </p>

        <SignalMeter className="mb-4" value={project.progress} label="Readiness" />

        <div className="sf-meta flex items-center justify-between border-t border-border pt-3 text-foreground-secondary">
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
          <ChevronRight className="h-5 w-5 text-accent" />
        </div>
      </Card>
    </Link>
  );
}

/* ------------------------------------------------------------------ */
/*  List Row Variant                                                   */
/* ------------------------------------------------------------------ */

function ProjectListRow({ project }: { project: ProjectResponse }) {
  return (
    <Link href={`/projects/${project.id}`}>
      <Card hover className="group cursor-pointer">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-4 min-w-0">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-accent/25 bg-accent-light text-accent">
              {TYPE_ICON[project.type] ?? <Briefcase className="h-5 w-5" />}
            </div>
            <div className="min-w-0">
              <h3 className="truncate text-sm font-semibold text-foreground group-hover:text-accent transition-colors">
                {project.name}
              </h3>
              <p className="sf-meta truncate text-foreground-secondary">
                {TYPE_LABEL[project.type] ?? project.type}
              </p>
            </div>
          </div>

          <div className="sf-meta flex items-center gap-4 text-foreground-secondary">
            <SignalMeter className="hidden w-28 md:grid" value={project.progress} label="Readiness" />
            <span className="inline-flex items-center gap-1.5">
              <FileText className="h-3.5 w-3.5" />
              {project.documentCount}
            </span>
            <span className="inline-flex items-center gap-1.5">
              <Clock className="h-3.5 w-3.5" />
              {formatRelativeTime(project.updatedAt)}
            </span>
            <StatusBadge status={project.status} />
            <ChevronRight className="h-4 w-4 text-foreground-secondary opacity-0 transition-opacity group-hover:opacity-100" />
          </div>
        </div>
      </Card>
    </Link>
  );
}
