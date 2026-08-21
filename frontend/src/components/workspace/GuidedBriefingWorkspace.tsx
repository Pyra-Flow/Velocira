"use client";

import { useCallback, useEffect, useState } from "react";
import { CheckCircle2, ChevronDown, Loader2, RotateCcw, Sparkles, X } from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import InterviewPanel from "@/components/interview/InterviewPanel";
import SrsWorkspace from "@/components/srs/SrsWorkspace";
import DocumentationPackageWorkspace from "@/components/documentation/DocumentationPackageWorkspace";
import {
  documentApi,
  generationJobApi,
  projectGenerationApi,
  type DocumentResponse,
  type ProjectGenerationResponse,
  type ProjectResponse,
} from "@/lib/api";

type Props = {
  project: ProjectResponse;
  generationUnlocked: boolean;
  projectActionSubmitting: boolean;
  onRefresh: () => Promise<unknown>;
  onArchiveToggle: () => Promise<void>;
  onProjectUpdated: () => void;
};

const ACTIVE_STAGES = new Set(["UNDERSTANDING", "CREATING", "FINISHING"]);

function key(prefix: string) {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? `${prefix}-${crypto.randomUUID()}`
    : `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function WorkspaceLoading() {
  return <div className="flex min-h-screen items-center justify-center bg-background-secondary" role="status" aria-label="Loading project"><Loader2 className="h-7 w-7 animate-spin text-accent" /></div>;
}

function ProjectPlanPreview({ content }: { content: string }) {
  const sections = content
    .split(/^##\s+/m)
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part, index) => {
      const [heading, ...body] = part.split("\n");
      return { heading: heading || `Section ${index + 1}`, body: body.join("\n").trim() };
    });

  if (!sections.length) return <p className="whitespace-pre-wrap text-sm leading-7 text-foreground-secondary">{content}</p>;
  return <div className="space-y-6">{sections.map((section) => <section key={section.heading}><h4 className="text-sm font-semibold text-foreground">{section.heading}</h4><p className="mt-2 whitespace-pre-wrap text-sm leading-7 text-foreground-secondary">{section.body}</p></section>)}</div>;
}

function originalIdea(description: string) {
  return description.split("\n\n", 1)[0]?.trim() || description;
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
  const [generation, setGeneration] = useState<ProjectGenerationResponse | null>(null);
  const [result, setResult] = useState<DocumentResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionError, setActionError] = useState<string | null>(null);
  const [refinementOpen, setRefinementOpen] = useState(false);
  const [refinement, setRefinement] = useState("");
  const [submittingRefinement, setSubmittingRefinement] = useState(false);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [documentRevision, setDocumentRevision] = useState(0);
  const [slowGeneration, setSlowGeneration] = useState(false);
  const [startingGeneration, setStartingGeneration] = useState(false);
  const [interviewReady, setInterviewReady] = useState(generationUnlocked);
  const stage = generation?.stage ?? "NEEDS_INPUT";
  const isActive = ACTIVE_STAGES.has(stage);
  const isReady = stage === "READY";
  const hasPreview = isReady || stage === "PARTIAL";
  const needsDiscovery = ["DRAFT", "DISCOVERY"].includes(project.status);
  const readyToGenerate = stage === "NEEDS_INPUT" && project.status === "READY_FOR_GENERATION";
  const canGenerateFromDiscovery = !isActive && project.status === "READY_FOR_GENERATION";
  const srsGenerationReady = generationUnlocked || interviewReady;

  const continueToSrs = () => {
    window.setTimeout(() => {
      document.getElementById("srs-heading")?.scrollIntoView({ behavior: "smooth", block: "start" });
    }, 0);
  };

  const load = useCallback(async () => {
    try {
      const response = await projectGenerationApi.status(project.id);
      if (!response.success || !response.data) {
        setActionError(response.message || "We couldn’t check generation right now. Please try again.");
        setLoading(false);
        return;
      }
      setGeneration(response.data);
      setActionError(null);
      const documentId = response.data.generation?.documentId;
      if (["READY", "PARTIAL"].includes(response.data.stage) && documentId) {
        const document = await documentApi.get(project.id, documentId);
        if (document.success && document.data) setResult(document.data);
        else setActionError(document.message || "We couldn't load your project plan. Please try again.");
      }
      setLoading(false);
    } catch {
      setActionError("We can't reach the generation service. Check your connection, then try again.");
      setLoading(false);
    }
  }, [project.id]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  useEffect(() => {
    if (!generation || !ACTIVE_STAGES.has(generation.stage)) return;
    const timer = window.setInterval(() => { void load(); }, 1_500);
    return () => window.clearInterval(timer);
  }, [generation, load]);

  useEffect(() => {
    if (!isActive) return;
    const timer = window.setTimeout(() => setSlowGeneration(true), 15_000);
    return () => window.clearTimeout(timer);
  }, [isActive, stage]);

  const cancel = async () => {
    const jobId = generation?.generation?.id ?? null;
    if (!jobId) return;
    try {
      const response = await generationJobApi.cancel(project.id, jobId);
      if (!response.success) setActionError(response.message || "We couldn’t cancel generation. Please try again.");
      await load();
      onProjectUpdated();
    } catch {
      setActionError("We couldn't cancel generation because the connection was interrupted. Please try again.");
    }
  };

  const retry = async () => {
    const jobId = generation?.generation?.id ?? null;
    if (!jobId) return;
    setSlowGeneration(false);
    try {
      const response = await generationJobApi.retry(project.id, jobId, key("retry"));
      if (!response.success) setActionError(response.message || "We couldn’t restart generation. Please try again.");
      await load();
      onProjectUpdated();
    } catch {
      setActionError("We couldn't restart generation because the connection was interrupted. Please try again.");
    }
  };

  const generateProject = async () => {
    setStartingGeneration(true);
    setSlowGeneration(false);
    setActionError(null);
    try {
      const response = await projectGenerationApi.generate(project.id, key("generate-project"));
      if (!response.success || !response.data) {
        setActionError(response.message || "We couldn’t start generation. Your answers are still here—try again.");
        return;
      }
      setGeneration(response.data);
      await load();
      onProjectUpdated();
    } catch {
      setActionError("We couldn’t start generation because the connection was interrupted. Your answers are still here—try again.");
    } finally {
      setStartingGeneration(false);
    }
  };

  const refine = async () => {
    if (!refinement.trim()) {
      setActionError("Tell us what you would like to change.");
      return;
    }
    setSubmittingRefinement(true);
    setSlowGeneration(false);
    setActionError(null);
    try {
      const response = await projectGenerationApi.refine(project.id, { message: refinement.trim() }, key("refine"));
      if (!response.success || !response.data) {
        setActionError(response.message || "We couldn’t apply that update. Your request is still here—try again.");
        return;
      }
      setGeneration(response.data);
      setResult(null);
      setRefinement("");
      setRefinementOpen(false);
      onProjectUpdated();
    } catch {
      setActionError("We couldn't apply that update because the connection was interrupted. Your request is still here—try again.");
    } finally {
      setSubmittingRefinement(false);
    }
  };

  if (loading) return <WorkspaceLoading />;

  const jobMessage = generation?.detail ?? "Your project is saved and ready when you are.";

  return (
    <main className="workspace-page">
      <div className="workspace-page__inner mx-auto max-w-3xl">
        <header className="max-w-2xl">
          <p className="sf-meta text-accent">Project</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight text-foreground">{project.name}</h1>
          <p className="mt-3 text-base leading-7 text-foreground-secondary">{originalIdea(project.description)}</p>
        </header>

        {isActive && generation && (
          <Card className="mt-8 p-6 sm:p-8" role="status" aria-live="polite">
            <div className="flex items-start gap-4">
              <span className="mt-0.5 grid h-10 w-10 shrink-0 place-items-center rounded-full bg-accent-light text-accent"><Loader2 className="h-5 w-5 animate-spin" /></span>
              <div>
                <p className="sf-meta text-accent">Creating your project</p>
                <h2 className="mt-2 text-2xl font-semibold text-foreground">{generation.headline}</h2>
                <p className="mt-2 max-w-xl text-sm leading-6 text-foreground-secondary">{jobMessage}</p>
                {slowGeneration && <p className="mt-3 text-sm leading-6 text-foreground-secondary">This is taking a little longer than usual. You can keep this page open, or come back later—your idea is saved.</p>}
                <ol className="mt-6 grid gap-2 text-sm sm:grid-cols-3" aria-label="Generation progress">
                  {["Understanding", "Creating", "Finishing"].map((item, index) => {
                    const complete = (stage === "CREATING" && index === 0) || (stage === "FINISHING" && index < 2);
                    const current = (stage === "UNDERSTANDING" && index === 0) || (stage === "CREATING" && index === 1) || (stage === "FINISHING" && index === 2);
                    return <li key={item} className={`rounded-md border px-3 py-2 ${complete || current ? "border-accent/35 bg-accent-light text-foreground" : "border-border text-foreground-secondary"}`}>{complete ? "✓ " : current ? "• " : ""}{item}</li>;
                  })}
                </ol>
                <div className="mt-6 flex flex-wrap gap-4">
                  {generation.canCancel && <button type="button" onClick={() => void cancel()} className="text-sm text-foreground-secondary underline-offset-4 hover:text-foreground hover:underline">Cancel generation</button>}
                  {actionError && <button type="button" onClick={() => void load()} className="text-sm text-foreground-secondary underline-offset-4 hover:text-foreground hover:underline">Check connection</button>}
                </div>
              </div>
            </div>
          </Card>
        )}

        {hasPreview && (
          <section className="mt-8" aria-labelledby="result-heading">
            <Card className="p-6 sm:p-8">
              <div className="flex gap-4">
                <CheckCircle2 className="mt-0.5 h-7 w-7 shrink-0 text-success" />
                <div><p className="sf-meta text-success">{isReady ? "Ready" : "Partially ready"}</p><h2 id="result-heading" className="mt-2 text-2xl font-semibold text-foreground">Your first project plan</h2><p className="mt-2 text-sm leading-6 text-foreground-secondary">{generation?.detail}</p></div>
              </div>
              {result ? <article className="mt-7 border-t border-border pt-6"><h3 className="text-lg font-semibold text-foreground">{result.title}</h3><div className="mt-5"><ProjectPlanPreview content={result.content ?? ""} /></div></article> : <div className="mt-7 flex flex-wrap items-center gap-3 border-t border-border pt-6 text-sm text-foreground-secondary"><span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin text-accent" /> Loading your result…</span>{actionError && <Button size="sm" variant="outline" onClick={() => void load()}>Try again</Button>}</div>}
              <div className="mt-7 border-t border-border pt-6">
                {!refinementOpen ? <div className="flex flex-wrap gap-2">{canGenerateFromDiscovery && <Button onClick={() => void generateProject()} loading={startingGeneration} icon={<Sparkles className="h-4 w-4" />}>Generate updated project</Button>}<Button variant={canGenerateFromDiscovery ? "outline" : "primary"} onClick={() => setRefinementOpen(true)} icon={<Sparkles className="h-4 w-4" />}>Continue editing</Button></div> : (
                  <div><label htmlFor="refinement" className="text-sm font-semibold text-foreground">What would you like to change?</label><textarea id="refinement" value={refinement} onChange={(event) => setRefinement(event.target.value)} rows={4} placeholder="For example: Make this work for multiple salon locations." className="mt-2 w-full resize-y rounded-md border border-input-border bg-input-bg px-4 py-3 text-foreground placeholder:text-placeholder focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20" /><div className="mt-3 flex flex-wrap gap-2"><Button onClick={() => void refine()} loading={submittingRefinement}>Update project</Button><Button variant="ghost" onClick={() => { setRefinementOpen(false); setRefinement(""); }}>Cancel</Button></div></div>
                )}
              </div>
            </Card>
          </section>
        )}

        {needsDiscovery && (
          <section className="mt-8" aria-labelledby="discovery-start-heading">
            <div className="mb-5 max-w-2xl">
              <p className="sf-meta text-accent">Before we generate</p>
              <h2 id="discovery-start-heading" className="mt-2 text-2xl font-semibold text-foreground">Answer a few focused questions</h2>
              <p className="mt-2 text-sm leading-6 text-foreground-secondary">Your description helps tailor the questions. Each answer is yours—we never copy your idea into the other answers.</p>
            </div>
            <InterviewPanel
              projectId={project.id}
              onReadinessChanged={setInterviewReady}
              onContinueToSrs={continueToSrs}
              onUpdated={() => { setDocumentRevision((current) => current + 1); onProjectUpdated(); }}
            />
          </section>
        )}

        {!isActive && srsGenerationReady && (
          <section className="mt-8" aria-label="SRS generation">
            <SrsWorkspace projectId={project.id} projectName={project.name} projectDescription={project.description} generationUnlocked={srsGenerationReady} onUpdated={() => { setDocumentRevision((current) => current + 1); onProjectUpdated(); }} />
          </section>
        )}

        {!needsDiscovery && !isActive && !hasPreview && (
          <Card className="mt-8 p-6" role={stage === "FAILED" ? "alert" : "status"}>
            <h2 className="text-xl font-semibold text-foreground">{readyToGenerate ? "Your project is ready to generate." : generation?.headline ?? "Your project is ready to generate."}</h2>
            <p className="mt-2 text-sm leading-6 text-foreground-secondary">{readyToGenerate ? "Your answers are saved. Generate your first project plan whenever you are ready." : jobMessage}</p>
            <div className="mt-5 flex flex-wrap gap-3">
              {readyToGenerate && <Button onClick={() => void generateProject()} loading={startingGeneration} icon={<Sparkles className="h-4 w-4" />}>Generate project plan</Button>}
              {generation?.canRetry && <Button onClick={() => void retry()} icon={<RotateCcw className="h-4 w-4" />}>Try again</Button>}
              {actionError && <Button variant="outline" onClick={() => void load()}>Check connection</Button>}
              {!readyToGenerate && <Button variant={generation?.canRetry ? "outline" : "primary"} onClick={() => setRefinementOpen(true)}>Update idea</Button>}
            </div>
          </Card>
        )}

        {refinementOpen && !needsDiscovery && !hasPreview && !isActive && (
          <Card className="mt-5 p-6">
            <label htmlFor="refinement" className="text-sm font-semibold text-foreground">What would you like to change?</label>
            <textarea id="refinement" value={refinement} onChange={(event) => setRefinement(event.target.value)} rows={4} placeholder="For example: Add multiple locations, but keep the first version simple." className="mt-2 w-full resize-y rounded-md border border-input-border bg-input-bg px-4 py-3 text-foreground placeholder:text-placeholder focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20" />
            <div className="mt-3 flex flex-wrap gap-2"><Button onClick={() => void refine()} loading={submittingRefinement}>Generate updated project</Button><Button variant="ghost" onClick={() => { setRefinementOpen(false); setRefinement(""); }}>Cancel</Button></div>
          </Card>
        )}

        {actionError && <p className="mt-5 rounded-md border border-error/25 bg-error/5 px-3 py-2.5 text-sm text-error" role="alert">{actionError}</p>}

        {!isActive && !needsDiscovery && (
          <details className="mt-8 border-t border-border pt-5" open={advancedOpen} onToggle={(event) => setAdvancedOpen((event.currentTarget as HTMLDetailsElement).open)}>
            <summary className="flex cursor-pointer list-none items-center gap-2 text-sm font-medium text-foreground focus:outline-none focus:ring-2 focus:ring-accent/30"><ChevronDown className="h-4 w-4" /> Advanced project details</summary>
            <div className="mt-6 space-y-8">
              <InterviewPanel projectId={project.id} onReadinessChanged={setInterviewReady} onContinueToSrs={continueToSrs} onUpdated={() => { setDocumentRevision((current) => current + 1); onProjectUpdated(); }} />
              <DocumentationPackageWorkspace projectId={project.id} refreshVersion={documentRevision} onUpdated={onProjectUpdated} />
              <div className="flex flex-wrap gap-2 border-t border-border pt-6"><Button variant="ghost" onClick={() => void onRefresh()}>Refresh project</Button><Button variant="ghost" onClick={() => void onArchiveToggle()} disabled={projectActionSubmitting} icon={<X className="h-4 w-4" />}>{project.status === "ARCHIVED" ? "Restore project" : "Archive project"}</Button></div>
            </div>
          </details>
        )}
      </div>
    </main>
  );
}
