"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { ArrowRight, Clock, FileText, FolderOpen, Plus } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { useProjectStore } from "@/store/projectStore";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import StatusBadge from "@/components/ui/StatusBadge";
import SignalMeter from "@/components/ui/SignalMeter";
import { PageTransition } from "@/components/ui/Animations";
import WorkspacePageHeader from "@/components/workspace/WorkspacePageHeader";
import ProjectHealthPanel from "@/components/workspace/ProjectHealthPanel";
import { formatRelativeTime } from "@/lib/utils";
import type { ProjectResponse } from "@/lib/api";

type ProjectFilter = "ALL" | "ACTIVE" | "APPROVED";

function projectTypeLabel(type: string) {
  return type
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function ProjectCard({ project }: { project: ProjectResponse }) {
  return (
    <Link href={`/projects/${project.id}`}>
      <Card hover className="sf-project-card group h-full cursor-pointer">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="truncate text-lg font-semibold text-foreground group-hover:text-accent">
                {project.name}
              </h2>
              <StatusBadge status={project.status} />
            </div>
            <p className="sf-meta mt-1 text-foreground-secondary">
              {projectTypeLabel(project.type)}
            </p>
          </div>
          <ArrowRight className="mt-1 h-4 w-4 shrink-0 text-foreground-secondary transition-transform group-hover:translate-x-1 group-hover:text-accent" />
        </div>

        <p className="mt-4 line-clamp-2 text-sm text-foreground-secondary">
          {project.description}
        </p>

        <SignalMeter className="mt-5" value={project.progress} label="Readiness" />

        <div className="sf-meta mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-border pt-4 text-foreground-secondary">
          <span className="flex items-center gap-1.5">
            <FileText className="h-3.5 w-3.5" />
            {project.documentCount} {project.documentCount === 1 ? "document" : "documents"}
          </span>
          <span className="flex items-center gap-1.5">
            <Clock className="h-3.5 w-3.5" />
            {formatRelativeTime(project.updatedAt)}
          </span>
        </div>
      </Card>
    </Link>
  );
}

function ProjectTable({ projects }: { projects: ProjectResponse[] }) {
  return (
    <div className="sf-project-table" role="region" aria-label="Projects">
      <div className="sf-project-table__head" role="row">
        <span role="columnheader">Project</span>
        <span role="columnheader">Lifecycle</span>
        <span role="columnheader">Readiness</span>
        <span role="columnheader">Last signal</span>
        <span role="columnheader">Next move</span>
      </div>
      {projects.map((project) => (
        <Link key={project.id} href={`/projects/${project.id}`} className="sf-project-table__row" aria-label={`Open ${project.name}`}>
          <span className="sf-project-table__project"><span className="sf-project-table__file"><FileText className="h-4 w-4" /></span><span><strong>{project.name}</strong><small>{projectTypeLabel(project.type)}</small></span></span>
          <span><StatusBadge status={project.status} /></span>
          <span className="sf-project-table__meter"><SignalMeter value={project.progress} label={`${project.progress}%`} /></span>
          <span className="sf-project-table__updated"><Clock className="h-3.5 w-3.5" />{formatRelativeTime(project.updatedAt)}</span>
          <span className="sf-project-table__action">Open workspace <ArrowRight className="h-3.5 w-3.5" /></span>
        </Link>
      ))}
      <Link href="/projects/new" className="sf-project-table__create"><Plus className="h-5 w-5" /><span><strong>Create the next project</strong><small>Start with a brief and add context when it is useful.</small></span><ArrowRight className="h-4 w-4" /></Link>
    </div>
  );
}

export default function DashboardPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading } = useAuthStore();
  const { projects, isLoading: projectsLoading, error, fetchProjects } = useProjectStore();
  const [filter, setFilter] = useState<ProjectFilter>("ALL");

  useEffect(() => {
    if (!authLoading && !isAuthenticated) router.replace("/login");
  }, [authLoading, isAuthenticated, router]);

  useEffect(() => {
    if (isAuthenticated) fetchProjects({ page: 0, size: 50 });
  }, [fetchProjects, isAuthenticated]);

  const filteredProjects = useMemo(() => {
    if (filter === "ACTIVE") {
      return projects.filter((project) => ["DRAFT", "DISCOVERY", "READY_FOR_GENERATION", "GENERATING", "NEEDS_REVIEW", "FAILED"].includes(project.status));
    }
    if (filter === "APPROVED") return projects.filter((project) => project.status === "APPROVED");
    return projects;
  }, [filter, projects]);

  if (authLoading || !isAuthenticated) {
    return <div className="min-h-[calc(100vh-4rem)]" role="status" aria-live="polite"><span className="sr-only">Loading your workspace</span></div>;
  }

  const filters: { key: ProjectFilter; label: string; count: number }[] = [
    { key: "ALL", label: "All", count: projects.length },
    {
      key: "ACTIVE",
      label: "Active",
      count: projects.filter((project) => ["DRAFT", "DISCOVERY", "READY_FOR_GENERATION", "GENERATING", "NEEDS_REVIEW", "FAILED"].includes(project.status)).length,
    },
    { key: "APPROVED", label: "Approved", count: projects.filter((project) => project.status === "APPROVED").length },
  ];
  const activeCount = filters.find((item) => item.key === "ACTIVE")?.count ?? 0;
  const approvedCount = filters.find((item) => item.key === "APPROVED")?.count ?? 0;

  return (
    <PageTransition>
      <section className="workspace-page">
        <div className="workspace-page__inner">
          <WorkspacePageHeader
            eyebrow="Projects"
            title="Your projects"
            description="Create something new or pick up where you left off."
            actions={projects.length > 0 ? <Link href="/projects/new"><Button icon={<Plus className="h-4 w-4" />}>New project</Button></Link> : undefined}
          />
          {projects.length > 0 && <><div className="workspace-stat-strip mb-8">
            {[
              { label: "Total projects", value: projects.length },
              { label: "In progress", value: activeCount },
              { label: "Approved", value: approvedCount },
            ].map((item) => <div key={item.label}><p>{item.label}</p><strong>{item.value}</strong></div>)}
          </div>

          <div className="mb-8"><ProjectHealthPanel project={projects[0]} /></div>

        <div className="mb-6 flex flex-col gap-3 border-b border-border pb-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-base font-semibold text-foreground">Your workspace</h2>
            <p className="mt-1 text-sm text-foreground-secondary">Filter by lifecycle state.</p>
          </div>
          <div className="sf-filter-row flex w-fit gap-1 rounded-xl border border-border bg-background-secondary/80 p-1">
          {filters.map(({ key, label, count }) => (
            <button
              key={key}
              type="button"
              onClick={() => setFilter(key)}
              aria-pressed={filter === key}
              className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                filter === key ? "bg-accent-light text-accent" : "text-foreground-secondary hover:bg-card/60 hover:text-foreground"
              }`}
            >
              {label} ({count})
            </button>
          ))}
          </div>
        </div></>}

        {projectsLoading ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" role="status" aria-live="polite" aria-label="Loading projects">
            {[0, 1, 2].map((item) => <div key={item} className="workspace-skeleton h-48" />)}
          </div>
        ) : error ? (
          <Card className="border-error/25 bg-error/5"><p className="text-sm text-error" role="alert">{error}</p><Button className="mt-4" variant="outline" size="sm" onClick={() => void fetchProjects({ page: 0, size: 50 })}>Try again</Button></Card>
        ) : filteredProjects.length === 0 ? (
          <div className="workspace-empty-state"><FolderOpen aria-hidden="true" /><h2>{projects.length === 0 ? "Start your first project" : "No projects in this view"}</h2><p>{projects.length === 0 ? "Describe what you want to create, then get a first project plan." : "Choose a different lifecycle filter to see more projects."}</p>{projects.length === 0 && <Link href="/projects/new"><Button size="sm" icon={<Plus className="h-4 w-4" />}>Create project</Button></Link>}</div>
        ) : (
          <div className="space-y-5">
            <div className="hidden lg:block"><ProjectTable projects={filteredProjects} /></div>
            <motion.div layout className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:hidden">
              {filteredProjects.map((project) => <ProjectCard key={project.id} project={project} />)}
            </motion.div>
          </div>
        )}
        </div>
      </section>
    </PageTransition>
  );
}
