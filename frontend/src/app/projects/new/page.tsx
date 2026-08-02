"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AnimatePresence, motion } from "framer-motion";
import {
  ArrowLeft,
  ArrowRight,
  BrainCircuit,
  Check,
  CircleHelp,
  FileText,
  Loader2,
  Sparkles,
} from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import { FadeIn, PageTransition } from "@/components/ui/Animations";
import WorkspacePageHeader from "@/components/workspace/WorkspacePageHeader";
import { useAuthStore } from "@/store/authStore";
import { useProjectStore } from "@/store/projectStore";
import type { ProjectType } from "@/lib/api";

const PROJECT_TYPES: Array<{ value: ProjectType; label: string }> = [
  { value: "WEB_APP", label: "Web app" },
  { value: "MOBILE_APP", label: "Mobile app" },
  { value: "AI_SYSTEM", label: "AI system" },
  { value: "IOT", label: "IoT product" },
  { value: "DESKTOP_APP", label: "Desktop app" },
  { value: "API_BACKEND", label: "API / backend" },
];

type DiscoveryContext = {
  label: string;
  summary: string;
  type: ProjectType;
  questions: string[];
};

const CONTEXTS: Array<DiscoveryContext & { keywords: string[] }> = [
  {
    label: "Healthcare product",
    summary: "The discovery planner will prioritise patient safety, clinical roles, privacy, approvals, and integrations.",
    type: "WEB_APP",
    keywords: ["clinic", "health", "patient", "doctor", "medical", "hospital", "care", "ehr", "appointment"],
    questions: ["roles and care workflow", "sensitive data", "clinical integrations"],
  },
  {
    label: "Fintech product",
    summary: "The discovery planner will prioritise money movement, permissions, fraud risk, auditability, and regulatory constraints.",
    type: "WEB_APP",
    keywords: ["payment", "bank", "finance", "fintech", "wallet", "invoice", "transaction", "loan"],
    questions: ["money flow", "approvals", "compliance obligations"],
  },
  {
    label: "Commerce product",
    summary: "The discovery planner will prioritise catalogues, checkout, fulfilment, inventory, and customer support workflows.",
    type: "WEB_APP",
    keywords: ["shop", "store", "commerce", "ecommerce", "marketplace", "order", "catalog", "inventory"],
    questions: ["order lifecycle", "payments", "fulfilment"],
  },
  {
    label: "Education product",
    summary: "The discovery planner will prioritise learner journeys, educator roles, assessment, content, and accessibility.",
    type: "WEB_APP",
    keywords: ["course", "student", "teacher", "school", "learning", "education", "training", "exam"],
    questions: ["learning journey", "roles", "assessment rules"],
  },
  {
    label: "AI product",
    summary: "The discovery planner will prioritise human oversight, input/output safety, evaluation, data boundaries, and fallbacks.",
    type: "AI_SYSTEM",
    keywords: ["ai", "llm", "assistant", "agent", "model", "machine learning", "copilot", "rag"],
    questions: ["human oversight", "model inputs", "safety boundaries"],
  },
  {
    label: "Mobile product",
    summary: "The discovery planner will prioritise mobile journeys, devices, offline behaviour, notifications, and app-store constraints.",
    type: "MOBILE_APP",
    keywords: ["mobile", "ios", "android", "phone", "app"],
    questions: ["on-the-go workflow", "device capabilities", "offline needs"],
  },
  {
    label: "Internal operations tool",
    summary: "The discovery planner will prioritise staff roles, approvals, operational hand-offs, reporting, and existing systems.",
    type: "WEB_APP",
    keywords: ["internal", "employee", "staff", "erp", "admin", "operations", "back office", "workflow"],
    questions: ["staff roles", "approval flow", "system hand-offs"],
  },
];

function detectContext(title: string, description: string): DiscoveryContext {
  const content = `${title} ${description}`.toLowerCase();
  const match = CONTEXTS
    .map((context) => ({ context, score: context.keywords.filter((word) => content.includes(word)).length }))
    .sort((left, right) => right.score - left.score)[0];
  if (match && match.score > 0) return match.context;
  return {
    label: "Digital product",
    summary: "The discovery planner will start with goals and users, then adapt to the product details you share.",
    type: "WEB_APP",
    questions: ["users and outcomes", "core workflow", "constraints and risks"],
  };
}

export default function NewProjectPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading } = useAuthStore();
  const { createProject, isSubmitting, error: projectError, clearError } = useProjectStore();
  const [name, setName] = useState("");
  const [type, setType] = useState<ProjectType>("WEB_APP");
  const [typeWasOverridden, setTypeWasOverridden] = useState(false);
  const [problem, setProblem] = useState("");
  const [error, setError] = useState<string | null>(null);

  const detectedContext = useMemo(() => detectContext(name, problem), [name, problem]);
  const intakeComplete = Boolean(name.trim()) && problem.trim().length >= 50;

  useEffect(() => {
    if (!authLoading && !isAuthenticated) router.replace("/login");
  }, [authLoading, isAuthenticated, router]);

  const createWorkspace = async () => {
    if (!name.trim()) {
      setError("Give the project a title so the discovery planner has useful context.");
      return;
    }
    if (problem.trim().length < 50) {
      setError("Add a little more context (at least 50 characters). The next screen will ask the focused follow-ups.");
      return;
    }
    setError(null);
    clearError();
    const project = await createProject({
      name: name.trim(),
      description: problem.trim(),
      type: typeWasOverridden ? type : detectedContext.type,
      industry: detectedContext.label,
    });
    if (project) router.push(`/projects/${project.id}`);
  };

  if (authLoading || !isAuthenticated) {
    return <div className="flex min-h-screen items-center justify-center bg-background-secondary"><Loader2 className="h-8 w-8 animate-spin text-accent" /></div>;
  }

  return (
    <PageTransition>
      <section className="workspace-page overflow-hidden">
        <div className="workspace-page__inner max-w-4xl">
          <FadeIn>
            <Link href="/projects" className="mb-7 inline-flex items-center gap-1.5 text-sm text-foreground-secondary transition-colors hover:text-accent">
              <ArrowLeft className="h-4 w-4" /> Back to projects
            </Link>
            <div className="rounded-md border border-border bg-card p-6 sm:p-8">
              <div className="sf-meta mb-5 flex items-center gap-2 text-accent">
                <Sparkles className="h-4 w-4" /> Smart discovery
              </div>
              <WorkspacePageHeader
                eyebrow="Create a project"
                title="Tell us the idea. We’ll find the right questions."
                description="Start with a title and short description. Velocira uses that context to shape a focused discovery conversation—not a generic form."
              />
              <div className="mt-6 flex items-center gap-3 text-sm text-foreground-secondary" aria-label="Project creation progress">
                <span className="flex h-7 w-7 items-center justify-center rounded-full bg-accent text-xs font-bold text-on-primary">1</span>
                <span className="font-medium text-foreground">Describe the idea</span>
                <span className="h-px flex-1 bg-border" />
                <span className="flex h-7 w-7 items-center justify-center rounded-full border border-border bg-card text-xs font-bold">2</span>
                <span>Answer tailored questions</span>
              </div>
            </div>
          </FadeIn>

          <FadeIn delay={0.05}>
            <form className="mt-6 space-y-5" onSubmit={(event) => { event.preventDefault(); void createWorkspace(); }}>
              <Card className="space-y-6 p-5 sm:p-7">
                <div className="flex items-start gap-3 rounded-md border border-accent/25 bg-accent-light p-4 text-sm text-foreground-secondary">
                  <BrainCircuit className="mt-0.5 h-5 w-5 shrink-0 text-accent" />
                  <p><span className="font-semibold text-foreground">Your project context comes first.</span> We use this brief to choose the questions that materially improve your SRS, use cases, data model, and API contract.</p>
                </div>

                <Input
                  label="Project title"
                  placeholder="e.g. ClinicFlow"
                  icon={<FileText className="h-4 w-4" />}
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  required
                  autoComplete="off"
                  aria-invalid={Boolean(error && !name.trim())}
                />

                <div className="space-y-2">
                  <div className="flex items-center justify-between gap-3">
                    <label htmlFor="project-description" className="text-sm font-semibold text-foreground">Short description</label>
                    <span className={`text-xs ${problem.trim().length >= 50 ? "text-success" : "text-foreground-secondary"}`}>{problem.trim().length}/50 minimum</span>
                  </div>
                  <textarea
                    id="project-description"
                    rows={6}
                    value={problem}
                    onChange={(event) => setProblem(event.target.value)}
                    placeholder="What are you building, who is it for, and what pain should it solve? Two or three plain-language sentences are enough to start."
                    className="w-full resize-y rounded-md border border-input-border bg-input-bg px-4 py-3 text-foreground placeholder:text-placeholder focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20"
                    aria-describedby="description-help"
                  />
                  <p id="description-help" className="text-xs text-foreground-secondary">Don’t worry about perfect requirements yet. The next step asks only the questions your idea still needs.</p>
                </div>

                <AnimatePresence mode="wait">
                  <motion.div
                    key={detectedContext.label}
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    transition={{ duration: 0.2 }}
                    className="rounded-md border border-accent/30 bg-accent-light p-4"
                    aria-live="polite"
                  >
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                      <div>
                        <p className="sf-meta text-accent">Likely project context</p>
                        <h2 className="mt-1 text-lg font-semibold text-foreground">{detectedContext.label}</h2>
                        <p className="mt-1 max-w-2xl text-sm text-foreground-secondary">{detectedContext.summary}</p>
                      </div>
                      <span className="status-badge status-badge--ready"><Check className="h-3.5 w-3.5" /> Context detected</span>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      {detectedContext.questions.map((question) => <span key={question} className="rounded-lg bg-card/80 px-2.5 py-1 text-xs text-foreground-secondary">{question}</span>)}
                    </div>
                  </motion.div>
                </AnimatePresence>

                <details className="rounded-xl border border-border bg-background-secondary/55 p-4">
                  <summary className="cursor-pointer text-sm font-medium text-foreground">Adjust the technical format (optional)</summary>
                  <div className="mt-3 space-y-1.5">
                    <label className="block text-sm text-foreground-secondary" htmlFor="project-type">Detected format can be corrected here.</label>
                    <select
                      id="project-type"
                      value={typeWasOverridden ? type : detectedContext.type}
                      onChange={(event) => { setType(event.target.value as ProjectType); setTypeWasOverridden(true); }}
                      className="w-full rounded-md border border-input-border bg-input-bg px-4 py-3 text-foreground focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20"
                    >
                      {PROJECT_TYPES.map((projectType) => <option key={projectType.value} value={projectType.value}>{projectType.label}</option>)}
                    </select>
                  </div>
                </details>
              </Card>

              {(error || projectError) && <Card className="border-error/30 bg-error/5"><p className="text-sm text-error" role="alert">{error || projectError}</p></Card>}

              <div className="flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
                <p className="flex items-center gap-2 text-xs text-foreground-secondary"><CircleHelp className="h-4 w-4 text-accent" /> You can edit every answer later. Unknowns stay visible.</p>
                <Button type="submit" loading={isSubmitting} disabled={isSubmitting} icon={<ArrowRight className="h-4 w-4" />} className="justify-center px-6">
                  {intakeComplete ? "Create tailored interview" : "Continue when ready"}
                </Button>
              </div>
            </form>
          </FadeIn>
        </div>
      </section>
    </PageTransition>
  );
}
