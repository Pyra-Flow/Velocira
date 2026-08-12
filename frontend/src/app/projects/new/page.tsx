"use client";

import { useEffect, useId, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Sparkles } from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import { PageTransition } from "@/components/ui/Animations";
import { useAuthStore } from "@/store/authStore";
import { projectGenerationApi } from "@/lib/api";

const EXAMPLES = [
  "A landing page for a fitness coaching business",
  "A portfolio for a product designer",
  "A dashboard for tracking customer feedback",
  "A booking app for a salon",
];

function idempotencyKey() {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? `project-${crypto.randomUUID()}`
    : `project-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export default function NewProjectPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading } = useAuthStore();
  const briefId = useId();
  const [brief, setBrief] = useState("");
  const [customizeOpen, setCustomizeOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [audience, setAudience] = useState("");
  const [include, setInclude] = useState("");
  const [avoid, setAvoid] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!authLoading && !isAuthenticated) router.replace("/login");
  }, [authLoading, isAuthenticated, router]);

  const generate = async () => {
    if (brief.trim().length < 10) {
      setError("Add a little more detail—who it is for or what it should help people do is enough.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const result = await projectGenerationApi.createProject({
        brief: brief.trim(),
        title: title.trim() || undefined,
        audience: audience.trim() || undefined,
        include: include.trim() || undefined,
        avoid: avoid.trim() || undefined,
      }, idempotencyKey());
      if (result.success && result.data) {
        router.push(`/projects/${result.data.project.id}`);
        return;
      }
      setError(result.message || "We couldn’t start your project. Your description is still here—try again.");
    } catch {
      setError("We can’t reach the project service. Check your connection, then try again.");
    } finally {
      setSubmitting(false);
    }
  };

  if (authLoading || !isAuthenticated) {
    return <div className="flex min-h-screen items-center justify-center bg-background-secondary" aria-label="Loading" />;
  }

  return (
    <PageTransition>
      <main className="workspace-page">
        <div className="workspace-page__inner mx-auto max-w-2xl">
          <Link href="/projects" className="mb-8 inline-flex items-center gap-2 text-sm text-foreground-secondary hover:text-accent">
            <ArrowLeft className="h-4 w-4" /> Back to projects
          </Link>
          <section aria-labelledby="create-project-heading">
            <p className="sf-meta flex items-center gap-2 text-accent"><Sparkles className="h-4 w-4" /> New project</p>
            <h1 id="create-project-heading" className="mt-3 text-3xl font-semibold tracking-tight text-foreground">Create a project</h1>
            <p className="mt-3 text-base leading-7 text-foreground-secondary">Describe what you want to make. You can refine the details later.</p>

            <Card className="mt-8 p-5 sm:p-7">
              <form onSubmit={(event) => { event.preventDefault(); void generate(); }} noValidate>
                <label htmlFor={briefId} className="block text-base font-semibold text-foreground">What would you like to create?</label>
                <p className="mt-1 text-sm text-foreground-secondary">Describe your idea in your own words.</p>
                <textarea
                  id={briefId}
                  value={brief}
                  onChange={(event) => setBrief(event.target.value)}
                  rows={6}
                  autoFocus
                  placeholder="For example: A booking app for a salon where clients can choose services, staff, and available times."
                  aria-describedby={`${briefId}-help ${error ? `${briefId}-error` : ""}`}
                  aria-invalid={Boolean(error)}
                  className="mt-4 w-full resize-y rounded-md border border-input-border bg-input-bg px-4 py-3 text-foreground placeholder:text-placeholder focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20"
                />
                <p id={`${briefId}-help`} className="mt-2 text-xs text-foreground-secondary">A sentence or two is enough to start.</p>

                <div className="mt-5" aria-label="Project idea examples">
                  <p className="text-xs font-medium uppercase tracking-wide text-foreground-secondary">Try an example</p>
                  <div className="mt-2 flex flex-wrap gap-2">
                    {EXAMPLES.map((example) => (
                      <button key={example} type="button" onClick={() => { setBrief(example); setError(null); }} className="rounded-full border border-border px-3 py-2 text-left text-xs text-foreground-secondary transition-colors hover:border-accent hover:text-foreground focus:outline-none focus:ring-2 focus:ring-accent/30">
                        {example}
                      </button>
                    ))}
                  </div>
                </div>

                <details className="mt-6 border-t border-border pt-5" open={customizeOpen} onToggle={(event) => setCustomizeOpen((event.currentTarget as HTMLDetailsElement).open)}>
                  <summary className="cursor-pointer text-sm font-medium text-foreground focus:outline-none focus:ring-2 focus:ring-accent/30">Customize <span className="font-normal text-foreground-secondary">(optional)</span></summary>
                  <div className="mt-4 grid gap-4">
                    <Input label="Project name" value={title} onChange={(event) => setTitle(event.target.value)} placeholder="We’ll name it for you if you leave this blank" />
                    <Input label="Who is this for?" value={audience} onChange={(event) => setAudience(event.target.value)} placeholder="For example: salon owners and their clients" />
                    <Input label="Anything important to include?" value={include} onChange={(event) => setInclude(event.target.value)} placeholder="For example: multiple locations" />
                    <Input label="Anything to avoid?" value={avoid} onChange={(event) => setAvoid(event.target.value)} placeholder="For example: payments in the first version" />
                  </div>
                </details>

                {error && <p id={`${briefId}-error`} className="mt-5 rounded-md border border-error/25 bg-error/5 px-3 py-2.5 text-sm text-error" role="alert">{error}</p>}
                <div className="mt-7 flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <p className="text-xs text-foreground-secondary">We’ll use this to tailor a few focused questions before generating your first version.</p>
                  <Button type="submit" loading={submitting} disabled={submitting} icon={<Sparkles className="h-4 w-4" />} className="justify-center">Continue to questions</Button>
                </div>
              </form>
            </Card>
          </section>
        </div>
      </main>
    </PageTransition>
  );
}
