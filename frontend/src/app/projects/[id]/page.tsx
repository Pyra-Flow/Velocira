"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { FolderOpen } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { useProjectStore } from "@/store/projectStore";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import GuidedBriefingWorkspace, { WorkspaceLoading } from "@/components/workspace/GuidedBriefingWorkspace";

export default function ProjectDetailPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { isAuthenticated, isLoading: authLoading } = useAuthStore();
  const { selectedProject: project, isLoading: projectLoading, error, fetchProject, archiveProject, restoreProject, isSubmitting, setSelectedProject } = useProjectStore();
  const [archiveConfirmOpen, setArchiveConfirmOpen] = useState(false);
  const [loadedProjectId, setLoadedProjectId] = useState<string | null>(null);

  useEffect(() => {
    if (!authLoading && !isAuthenticated) router.replace("/login");
  }, [authLoading, isAuthenticated, router]);

  useEffect(() => {
    if (!isAuthenticated || !id) return;
    let active = true;
    void fetchProject(id).then(() => {
      if (active) setLoadedProjectId(id);
    });
    return () => { active = false; setSelectedProject(null); };
  }, [fetchProject, id, isAuthenticated, setSelectedProject]);

  const refreshWorkspace = useCallback(async () => {
    if (id) await fetchProject(id);
  }, [fetchProject, id]);

  const handleArchiveToggle = async () => {
    if (!project) return;
    if (project.status === "ARCHIVED") { await restoreProject(project.id); return; }
    setArchiveConfirmOpen(true);
  };

  const confirmArchive = async () => {
    if (!project) return;
    const archived = await archiveProject(project.id);
    if (archived) setArchiveConfirmOpen(false);
  };

  if (authLoading || !isAuthenticated || loadedProjectId !== id || (projectLoading && !project)) return <WorkspaceLoading />;
  if (!project) {
    return <section className="mx-auto max-w-3xl px-4 py-16 sm:px-6"><Card className="text-center"><FolderOpen className="mx-auto mb-4 h-10 w-10 text-foreground-secondary/50" /><h1 className="text-xl font-semibold text-foreground">Project unavailable</h1><p className="mt-2 text-sm text-foreground-secondary">{error ?? "The project could not be found or you do not have access to it."}</p><Link href="/projects" className="mt-6 inline-block"><Button>Back to Projects</Button></Link></Card></section>;
  }

  // This is only a UI unlock. The server remains the authority and will reject
  // generation until the discovery questions have actually been completed.
  const generationUnlocked = !["DRAFT", "DISCOVERY", "ARCHIVED", "GENERATING"].includes(project.status);

  return <>
    <GuidedBriefingWorkspace project={project} generationUnlocked={generationUnlocked} projectActionSubmitting={isSubmitting} onRefresh={refreshWorkspace} onArchiveToggle={handleArchiveToggle} onProjectUpdated={() => { void refreshWorkspace(); }} />
    <ConfirmDialog open={archiveConfirmOpen} onOpenChange={setArchiveConfirmOpen} title="Archive project" description={`Archive ${project.name}? You can restore it later from the project actions menu.`} confirmLabel="Archive project" isSubmitting={isSubmitting} onConfirm={confirmArchive} />
  </>;
}
