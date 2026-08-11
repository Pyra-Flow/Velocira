"use client";

import { useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { Archive, FolderOpen, MoreHorizontal, RefreshCw, RotateCcw } from "lucide-react";
import InterviewPanel from "@/components/interview/InterviewPanel";
import SrsWorkspace from "@/components/srs/SrsWorkspace";
import DocumentationPackageWorkspace from "@/components/documentation/DocumentationPackageWorkspace";
import StatusBadge from "@/components/ui/StatusBadge";
import ProjectHealthPanel from "@/components/workspace/ProjectHealthPanel";
import ProjectLifecycle from "@/components/workspace/ProjectLifecycle";
import type { ProjectResponse } from "@/lib/api";

type Props = {
  project: ProjectResponse;
  generationUnlocked: boolean;
  projectActionSubmitting: boolean;
  onRefresh: () => Promise<unknown>;
  onArchiveToggle: () => Promise<void>;
  onProjectUpdated: () => void;
};

function WorkspaceLoading() {
  return (
    <div className="briefing-shell flex min-h-screen items-center justify-center px-5">
      <div className="briefing-loading" role="status" aria-live="polite" aria-label="Loading project workspace"><span className="sr-only">Loading project workspace</span><div className="briefing-loading__mark" /><div className="briefing-loading__line briefing-loading__line--wide" /><div className="briefing-loading__line" /></div>
    </div>
  );
}

export { WorkspaceLoading };

export default function GuidedBriefingWorkspace({
  project,
  generationUnlocked,
  projectActionSubmitting,
  onRefresh,
  onArchiveToggle,
  onProjectUpdated,
}: Props) {
  const [actionsOpen, setActionsOpen] = useState(false);
  const [documentRevision, setDocumentRevision] = useState(0);
  const actionsRef = useRef<HTMLDivElement | null>(null);
  const actionsTriggerRef = useRef<HTMLButtonElement | null>(null);
  const actionsMenuItemRef = useRef<HTMLButtonElement | null>(null);
  const handleProjectUpdated = () => {
    setDocumentRevision((current) => current + 1);
    onProjectUpdated();
  };

  useEffect(() => {
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!actionsRef.current?.contains(event.target as Node)) setActionsOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== "Escape" || !actionsOpen) return;
      event.preventDefault();
      setActionsOpen(false);
      window.requestAnimationFrame(() => actionsTriggerRef.current?.focus());
    };
    document.addEventListener("mousedown", closeOnOutsideClick);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("mousedown", closeOnOutsideClick);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [actionsOpen]);

  useEffect(() => {
    if (!actionsOpen) return;
    const frame = window.requestAnimationFrame(() => actionsMenuItemRef.current?.focus());
    return () => window.cancelAnimationFrame(frame);
  }, [actionsOpen]);

  const handleActionsMenuKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
      event.preventDefault();
      actionsMenuItemRef.current?.focus();
    }
  };

  return (
    <div className="briefing-shell">
      <div className="briefing-main space-y-8">
        <section className="briefing-project-context">
          <div>
            <p className="briefing-eyebrow">Project / {project.type.replaceAll("_", " ")}</p>
            <div className="flex flex-wrap items-center gap-3">
              <h1>{project.name}</h1>
              <StatusBadge status={project.status} />
            </div>
            <p className="briefing-project-context__description">{project.description}</p>
            <p className="briefing-project-context__meta">ID {project.id} {project.industry ? `• ${project.industry}` : ""}</p>
          </div>
          <div className="briefing-project-context__actions">
            <button className="briefing-icon-button" type="button" onClick={() => void onRefresh()} aria-label="Refresh project" title="Refresh project"><RefreshCw aria-hidden="true" /></button>
            <div className="relative" ref={actionsRef}>
              <button ref={actionsTriggerRef} className="briefing-icon-button" type="button" onClick={() => setActionsOpen((current) => !current)} onKeyDown={(event) => { if (["ArrowDown", "ArrowUp"].includes(event.key)) { event.preventDefault(); setActionsOpen(true); } }} aria-label="Project actions" aria-expanded={actionsOpen} aria-haspopup="menu" aria-controls="project-actions-menu"><MoreHorizontal aria-hidden="true" /></button>
              {actionsOpen && <div id="project-actions-menu" className="briefing-action-menu" role="menu" aria-label="Project actions" onKeyDown={handleActionsMenuKeyDown}><button ref={actionsMenuItemRef} type="button" role="menuitem" disabled={projectActionSubmitting} onClick={() => { setActionsOpen(false); void onArchiveToggle(); }}>{project.status === "ARCHIVED" ? <RotateCcw aria-hidden="true" /> : <Archive aria-hidden="true" />}{project.status === "ARCHIVED" ? "Restore project" : "Archive project"}</button></div>}
            </div>
          </div>
        </section>

        <section className="briefing-lifecycle" aria-label="Project lifecycle">
          <ProjectLifecycle status={project.status} />
        </section>

        <section className="briefing-workbench" aria-label="Discovery workspace">
          <div aria-label="Answer discovery questions">
            <InterviewPanel projectId={project.id} onUpdated={handleProjectUpdated} />
          </div>
          <aside className="briefing-workbench__health" aria-label="Project health">
            <ProjectHealthPanel project={project} />
          </aside>
        </section>

        <section className="space-y-6 border-t border-border pt-8" aria-label="Generate project documents">
          <div className="flex items-start gap-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-accent/25 bg-accent-light text-accent"><FolderOpen className="h-5 w-5" /></span>
            <div><p className="sf-meta text-accent">Documents</p><h2 className="mt-1 text-2xl font-semibold tracking-tight text-foreground">Generate everything from your answers.</h2><p className="mt-1 text-sm text-foreground-secondary">Use the project brief directly, or optionally add a file when you want extra source citations.</p></div>
          </div>
          <SrsWorkspace projectId={project.id} projectName={project.name} projectDescription={project.description} generationUnlocked={generationUnlocked} onUpdated={handleProjectUpdated} />
          <DocumentationPackageWorkspace projectId={project.id} generationUnlocked={generationUnlocked} refreshVersion={documentRevision} onUpdated={handleProjectUpdated} />
        </section>
      </div>
    </div>
  );
}
