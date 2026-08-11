"use client";

import Link from "next/link";
import { ArrowRight, Check, FileText, FolderKanban, Link2, ListChecks, Sparkles, WandSparkles } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import SignalForgeVisual from "@/components/ui/SignalForgeVisual";
import { FadeIn, PageTransition, StaggerContainer, StaggerItem } from "@/components/ui/Animations";

const highlights = [
  { icon: Sparkles, title: "Answer focused questions", copy: "Start with the idea. Velocira adapts its questions to the product you are building." },
  { icon: FileText, title: "Generate the SRS", copy: "Turn your answers into structured requirements with acceptance criteria and traceable sources." },
  { icon: FolderKanban, title: "Export the full package", copy: "Create use cases, ERD, OpenAPI, traceability, and exports from the same project context." },
];

const workflowStages = [
  { icon: FileText, number: "01", label: "Brief", copy: "Capture intent and define the decision to make." },
  { icon: Link2, number: "02", label: "Evidence", copy: "Attach the source context that supports it." },
  { icon: ListChecks, number: "03", label: "Requirements", copy: "Turn answers into reviewable requirements." },
  { icon: FolderKanban, number: "04", label: "Package", copy: "Keep documents and their traceability together." },
];

export default function LandingPage() {
  const { isAuthenticated } = useAuthStore();
  const primaryHref = isAuthenticated ? "/projects" : "/register";

  return (
    <PageTransition>
      <section className="public-page">
        <section className="public-hero public-hero--visual">
          <div className="public-hero__content">
            <p className="public-eyebrow"><WandSparkles className="h-4 w-4" /> From idea to governed requirements</p>
            <h1>Turn product ideas into governed, traceable requirements and documentation.</h1>
            <p className="public-hero__copy">Tell us what you are building. Answer a focused set of questions. Generate everything your team needs—without a maze of forms, gates, or disconnected tools.</p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link href={primaryHref}><Button size="lg" icon={<ArrowRight className="h-4 w-4" />}>{isAuthenticated ? "Open projects" : "Create your project"}</Button></Link>
              {!isAuthenticated && <Link href="/login"><Button size="lg" variant="outline">Sign in</Button></Link>}
            </div>
            <div className="public-flow" aria-label="How Velocira works"><span>1 · Describe</span><span>2 · Answer</span><span>3 · Generate</span></div>
          </div>
          <div className="public-workflow-board" aria-label="Documentation signal map">
            <div className="public-workflow-board__header"><span>Documentation signal map</span><span>BRIEF / PACKAGE</span></div>
            <div className="public-workflow-board__stages">
              {workflowStages.map(({ icon: Icon, number, label, copy }) => (
                <article key={label} className="public-workflow-stage">
                  <span className="public-workflow-stage__number">{number}</span>
                  <span className="public-workflow-stage__icon"><Icon className="h-5 w-5" /></span>
                  <h2>{label}</h2>
                  <p>{copy}</p>
                  <span className="public-workflow-stage__status"><Check className="h-3.5 w-3.5" /> Connected</span>
                </article>
              ))}
            </div>
            <div className="public-workflow-board__footer"><span>Traceability remains visible at every handoff</span><span>Ready for review</span></div>
          </div>
        </section>

        <section className="public-signal-strip" aria-label="Documentation workflow">
          <div><span>Input</span><strong>Context stays visible</strong></div>
          <div><span>Process</span><strong>Questions adapt to evidence</strong></div>
          <div><span>Output</span><strong>Artifacts stay linked</strong></div>
        </section>

        <StaggerContainer className="public-grid mt-6">
          {highlights.map(({ icon: Icon, title, copy }) => <StaggerItem key={title}><Card hover className="public-card"><span className="public-card__icon"><Icon className="h-5 w-5" /></span><h2>{title}</h2><p>{copy}</p></Card></StaggerItem>)}
        </StaggerContainer>

        <FadeIn className="sf-marketing-visual-grid">
          <div className="sf-visual-frame"><div className="sf-visual-frame__header"><p>From evidence to output</p><span>01 / 02</span></div><SignalForgeVisual name="traceability" label="Traceability path" /></div>
          <div className="sf-visual-frame"><div className="sf-visual-frame__header"><p>Documentation system</p><span>02 / 02</span></div><SignalForgeVisual name="documents" label="Package preview" /></div>
        </FadeIn>

        <FadeIn className="public-handoff mt-6">
          <div><p className="public-eyebrow">Review without context loss</p><h2>Every handoff keeps the evidence attached.</h2><p>Keep discovery, requirements, review, and exports connected to the same project record—so the next person sees why a decision exists, not just its final form.</p></div>
          <SignalForgeVisual name="collaboration" label="Collaboration handoff" />
        </FadeIn>
      </section>
    </PageTransition>
  );
}
