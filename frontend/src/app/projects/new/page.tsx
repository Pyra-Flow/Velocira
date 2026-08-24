"use client";

import { useEffect, useId, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, ArrowRight, Settings2 } from "lucide-react";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import { PageTransition } from "@/components/ui/Animations";
import { useAuthStore } from "@/store/authStore";
import { projectGenerationApi } from "@/lib/api";

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
  const [title, setTitle] = useState("");
  const [audience, setAudience] = useState("");
  const [include, setInclude] = useState("");
  const [avoid, setAvoid] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!authLoading && !isAuthenticated) router.replace("/login");
  }, [authLoading, isAuthenticated, router]);

  const createProject = async () => {
    const trimmedBrief = brief.trim();
    if (trimmedBrief.length < 10) {
      setError("Tell us a little more—what you want to make and who it should help is enough.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const result = await projectGenerationApi.createProject({
        brief: trimmedBrief,
        title: title.trim() || undefined,
        audience: audience.trim() || undefined,
        include: include.trim() || undefined,
        avoid: avoid.trim() || undefined,
      }, idempotencyKey());
      if (result.success && result.data) {
        router.push(`/projects/${result.data.project.id}?created=1`);
        return;
      }
      setError(result.message || "We couldn’t create the project. Your description is still here, so you can try again.");
    } catch {
      setError("We can’t reach the project service right now. Check your connection, then try again.");
    } finally {
      setSubmitting(false);
    }
  };

  if (authLoading || !isAuthenticated) {
    return <div className="flex min-h-screen items-center justify-center bg-background-secondary" role="status" aria-label="Loading project setup" />;
  }

  return (
    <PageTransition>
      <main className="workspace-page project-create-page">
        <div className="workspace-page__inner mx-auto max-w-3xl">
          <Link href="/projects" className="mb-5 inline-flex items-center gap-2 text-sm text-foreground-secondary hover:text-accent">
            <ArrowLeft className="h-4 w-4" /> Back to projects
          </Link>

          <ol className="journey-steps" aria-label="Project setup progress">
            <li className="is-current"><span>1</span><strong>Create</strong></li>
            <li><span>2</span><strong>Review brief</strong></li>
            <li><span>3</span><strong>Answer questions</strong></li>
          </ol>

          <header className="project-create-header">
            <p className="public-eyebrow">New project · about one minute</p>
            <h1>Create your project</h1>
            <p>Describe the product in your own words. Velocira will create a sensible starting point and ask for anything important that is missing.</p>
          </header>

          <form className="project-create-form" onSubmit={(event) => { event.preventDefault(); void createProject(); }} noValidate>
            <div>
              <label htmlFor={briefId}>What are you planning to build? <span>Required</span></label>
              <p id={`${briefId}-help`}>Include the main user and the outcome you want if you know them.</p>
              <textarea
                id={briefId}
                value={brief}
                onChange={(event) => { setBrief(event.target.value); if (error) setError(null); }}
                rows={3}
                autoFocus
                placeholder="For example: A client intake portal for a small legal practice where people can check eligibility, upload documents, and request an appointment."
                aria-describedby={`${briefId}-help ${error ? `${briefId}-error` : ""}`}
                aria-invalid={Boolean(error)}
              />
            </div>

            <details className="project-create-optional">
              <summary><Settings2 aria-hidden="true" /> Add optional context</summary>
              <p>These details can improve the first questions. You can also add them later.</p>
              <div className="project-create-optional__fields">
                <Input label="Project name" value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Leave blank for an automatic name" />
                <Input label="Primary audience" value={audience} onChange={(event) => setAudience(event.target.value)} placeholder="For example: prospective clients" />
                <Input label="Important to include" value={include} onChange={(event) => setInclude(event.target.value)} placeholder="For example: secure document uploads" />
                <Input label="Leave out for now" value={avoid} onChange={(event) => setAvoid(event.target.value)} placeholder="For example: online payments" />
              </div>
            </details>

            {error && <p id={`${briefId}-error`} className="project-create-error" role="alert">{error}</p>}

            <div className="project-create-actions">
              <p>Next: review a short project brief before any questions begin.</p>
              <Button type="submit" size="lg" loading={submitting} disabled={submitting} icon={<ArrowRight className="h-4 w-4" />}>Create project</Button>
            </div>
          </form>
        </div>
      </main>
    </PageTransition>
  );
}
