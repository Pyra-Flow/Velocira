"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowRight, ChevronLeft, ChevronRight, Clock3, FolderOpen, Loader2, Plus, Search } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { useProjectStore } from "@/store/projectStore";
import Button from "@/components/ui/Button";
import StatusBadge from "@/components/ui/StatusBadge";
import { PageTransition } from "@/components/ui/Animations";
import { formatRelativeTime } from "@/lib/utils";
import type { ProjectResponse, ProjectStatus } from "@/lib/api";

const FILTERS: { label: string; value: ProjectStatus | "ALL" }[] = [
  { label: "All projects", value: "ALL" },
  { label: "Discovery", value: "DISCOVERY" },
  { label: "Ready to generate", value: "READY_FOR_GENERATION" },
  { label: "Needs review", value: "NEEDS_REVIEW" },
  { label: "Approved", value: "APPROVED" },
  { label: "Archived", value: "ARCHIVED" },
];

function typeLabel(type: string) {
  return type.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function nextAction(project: ProjectResponse) {
  switch (project.status) {
    case "DRAFT":
    case "DISCOVERY": return "Continue questions";
    case "READY_FOR_GENERATION": return "Generate requirements";
    case "GENERATING": return "Check progress";
    case "NEEDS_REVIEW": return "Review output";
    case "APPROVED": return "Open documentation";
    case "FAILED": return "Resolve issue";
    case "ARCHIVED": return "View archived project";
  }
}

export default function ProjectsPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading } = useAuthStore();
  const { projects, totalElements, totalPages, currentPage, isLoading, error, fetchProjects } = useProjectStore();
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [status, setStatus] = useState<ProjectStatus | "ALL">("ALL");

  useEffect(() => {
    if (!authLoading && !isAuthenticated) router.replace("/login");
  }, [authLoading, isAuthenticated, router]);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [search]);

  const load = useCallback((page = 0) => fetchProjects({
    page,
    size: 12,
    search: debouncedSearch || undefined,
    status: status === "ALL" ? undefined : status,
  }), [debouncedSearch, fetchProjects, status]);

  useEffect(() => {
    if (isAuthenticated) void load(0);
  }, [isAuthenticated, load]);

  if (authLoading || !isAuthenticated) return <div className="workspace-route-loading" role="status"><Loader2 aria-hidden="true" /><span>Loading your projects…</span></div>;

  return (
    <PageTransition>
      <section className="workspace-page simple-projects">
        <div className="workspace-page__inner">
          <header className="simple-projects__header">
            <div><p className="public-eyebrow">Workspace</p><h1>Your projects</h1><p>Create something new or continue from the next clear step.</p></div>
            <Link href="/projects/new"><Button size="lg" icon={<Plus className="h-4 w-4" />}>New project</Button></Link>
          </header>

          {(totalElements > 3 || search || status !== "ALL") && (
            <div className="simple-projects__tools">
              <label><Search aria-hidden="true" /><span className="sr-only">Search projects</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search projects" /></label>
              <label><span className="sr-only">Filter by status</span><select value={status} onChange={(event) => setStatus(event.target.value as ProjectStatus | "ALL")}>{FILTERS.map((filter) => <option key={filter.value} value={filter.value}>{filter.label}</option>)}</select></label>
            </div>
          )}

          {isLoading ? (
            <div className="simple-projects__list" role="status" aria-label="Loading projects">{[0, 1, 2].map((item) => <div key={item} className="workspace-skeleton h-28" />)}</div>
          ) : error ? (
            <div className="simple-projects__message"><p role="alert">{error}</p><Button variant="outline" onClick={() => void load(currentPage)}>Try again</Button></div>
          ) : projects.length === 0 ? (
            <div className="simple-projects__empty"><FolderOpen aria-hidden="true" /><h2>{debouncedSearch || status !== "ALL" ? "No matching projects" : "Create your first project"}</h2><p>{debouncedSearch || status !== "ALL" ? "Clear the search or choose another status." : "Start with a short description. You’ll review the brief before any questions begin."}</p>{!debouncedSearch && status === "ALL" && <Link href="/projects/new"><Button icon={<Plus className="h-4 w-4" />}>Create project</Button></Link>}</div>
          ) : (
            <div className="simple-projects__list">
              {projects.map((project) => (
                <Link key={project.id} href={`/projects/${project.id}`} className="simple-project-row" aria-label={`${nextAction(project)} for ${project.name}`}>
                  <div className="simple-project-row__main"><div><h2>{project.name}</h2><StatusBadge status={project.status} /></div><p>{project.description}</p></div>
                  <div className="simple-project-row__meta"><span>{typeLabel(project.type)}</span><span><Clock3 aria-hidden="true" /> {formatRelativeTime(project.updatedAt)}</span></div>
                  <div className="simple-project-row__action"><span>{nextAction(project)}</span><ArrowRight aria-hidden="true" /></div>
                </Link>
              ))}
            </div>
          )}

          {!isLoading && projects.length > 0 && totalPages > 1 && (
            <nav className="simple-projects__pagination" aria-label="Project pages">
              <Button variant="outline" size="sm" icon={<ChevronLeft className="h-4 w-4" />} disabled={currentPage === 0} onClick={() => void load(currentPage - 1)}>Previous</Button>
              <span>Page {currentPage + 1} of {totalPages}</span>
              <Button variant="outline" size="sm" icon={<ChevronRight className="h-4 w-4" />} disabled={currentPage >= totalPages - 1} onClick={() => void load(currentPage + 1)}>Next</Button>
            </nav>
          )}
        </div>
      </section>
    </PageTransition>
  );
}
