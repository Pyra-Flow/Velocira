"use client";

import Link from "next/link";
import {
  ArrowRight,
  Check,
  CheckCircle2,
  Clock3,
  FileCheck2,
  ListChecks,
  ShieldCheck,
  Sparkles,
} from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import Button from "@/components/ui/Button";
import { FadeIn, PageTransition } from "@/components/ui/Animations";

const steps = [
  {
    number: "01",
    title: "Describe the product",
    copy: "Start with a sentence or two. Velocira creates the project and identifies what still needs a decision.",
  },
  {
    number: "02",
    title: "Answer focused questions",
    copy: "Work through one clear question at a time, with useful examples and progress that saves as you go.",
  },
  {
    number: "03",
    title: "Review and generate",
    copy: "Check the captured decisions, then generate a traceable SRS and connected documentation package.",
  },
];

const outcomes = [
  "A structured, review-ready SRS",
  "Requirements linked to the decisions behind them",
  "Use cases, test guidance, and exportable documentation",
];

export default function LandingPage() {
  const { isAuthenticated } = useAuthStore();
  const primaryHref = isAuthenticated ? "/projects" : "/register";

  return (
    <PageTransition>
      <div className="public-page landing-page">
        <section className="landing-hero" aria-labelledby="landing-title">
          <FadeIn className="landing-hero__copy">
            <p className="public-eyebrow"><Sparkles aria-hidden="true" className="h-4 w-4" /> Requirements planning, without the busywork</p>
            <h1 id="landing-title">Go from a rough idea to review-ready requirements.</h1>
            <p className="landing-hero__lead">Velocira helps product teams turn early context into clear requirements, decisions, and documentation through a short guided interview.</p>
            <div className="landing-hero__actions">
              <Link href={primaryHref}><Button size="lg" icon={<ArrowRight className="h-4 w-4" />}>{isAuthenticated ? "Open your projects" : "Create a project"}</Button></Link>
              {!isAuthenticated && <Link href="/login"><Button size="lg" variant="outline">Sign in</Button></Link>}
            </div>
            <p className="landing-hero__reassurance"><Clock3 aria-hidden="true" /> Create your first project in under a minute. Refine every answer later.</p>
          </FadeIn>

          <FadeIn delay={0.08} className="landing-preview" aria-label="Product preview">
            <div className="landing-preview__header">
              <div><span>Project brief</span><strong>Client intake portal</strong></div>
              <span className="landing-preview__status"><CheckCircle2 aria-hidden="true" /> In discovery</span>
            </div>
            <div className="landing-preview__progress" aria-label="Question progress">
              <div><span>Question 3 of 7</span><span>4 remaining</span></div>
              <span><i style={{ width: "43%" }} /></span>
            </div>
            <div className="landing-preview__question">
              <p>Scope · Required</p>
              <h2>What is the smallest complete outcome the first release must deliver?</h2>
              <div className="landing-preview__options" aria-hidden="true">
                <span><i /><strong>Complete one client intake journey</strong></span>
                <span><i /><strong>Include case review and approval</strong></span>
              </div>
            </div>
            <div className="landing-preview__saved"><Check aria-hidden="true" /> Answers save as you continue</div>
          </FadeIn>
        </section>

        <section className="landing-section" aria-labelledby="how-it-works">
          <div className="landing-section__heading">
            <p className="public-eyebrow">How it works</p>
            <h2 id="how-it-works">A clear path from idea to specification.</h2>
            <p>After you create an account, you describe the product, review a short project briefing, and answer only the questions needed to produce useful documentation.</p>
          </div>
          <ol className="landing-steps">
            {steps.map((step) => (
              <li key={step.number}>
                <span>{step.number}</span>
                <h3>{step.title}</h3>
                <p>{step.copy}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="landing-outcomes" aria-labelledby="outcomes-title">
          <div>
            <p className="public-eyebrow"><FileCheck2 aria-hidden="true" className="h-4 w-4" /> What you get</p>
            <h2 id="outcomes-title">Documentation your team can understand and trust.</h2>
            <p>Use Velocira for a new product, an internal workflow, a client brief, or a complex system that needs its assumptions made explicit.</p>
          </div>
          <ul>
            {outcomes.map((outcome) => <li key={outcome}><CheckCircle2 aria-hidden="true" /><span>{outcome}</span></li>)}
          </ul>
        </section>

        <section className="landing-trust" aria-label="Product principles">
          <div><ListChecks aria-hidden="true" /><span><strong>Focused by default</strong>One useful question at a time.</span></div>
          <div><ShieldCheck aria-hidden="true" /><span><strong>Your decisions stay visible</strong>Answers remain editable and traceable.</span></div>
          <div><FileCheck2 aria-hidden="true" /><span><strong>Built for review</strong>Outputs carry context, not just conclusions.</span></div>
        </section>

        <section className="landing-cta" aria-labelledby="landing-cta-title">
          <div><p className="public-eyebrow">Ready when you are</p><h2 id="landing-cta-title">Create the brief. Make the decisions. Generate the package.</h2></div>
          <Link href={primaryHref}><Button size="lg" icon={<ArrowRight className="h-4 w-4" />}>{isAuthenticated ? "Continue working" : "Start your first project"}</Button></Link>
        </section>
      </div>
    </PageTransition>
  );
}
