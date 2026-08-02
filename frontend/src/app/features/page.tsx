import { Bot, FileCheck2, GitBranch, PackageCheck, ShieldCheck, Sparkles } from "lucide-react";
import Card from "@/components/ui/Card";
import SignalForgeVisual from "@/components/ui/SignalForgeVisual";
import { FadeIn, PageTransition, StaggerContainer, StaggerItem } from "@/components/ui/Animations";

const features = [
  { icon: Bot, title: "Adaptive discovery", copy: "Questions respond to the title, description, project domain, and answers you have already given." },
  { icon: FileCheck2, title: "Structured SRS", copy: "Requirements include IDs, priorities, acceptance criteria, verification methods, and source context." },
  { icon: GitBranch, title: "Connected outputs", copy: "Use cases, ERD, OpenAPI, and traceability are generated from the same requirements." },
  { icon: PackageCheck, title: "One-click package", copy: "Generate the complete document set as soon as an SRS exists—without routing through extra screens." },
  { icon: ShieldCheck, title: "Evidence-aware", copy: "Your answers are the starting point; optional source files add richer citations when you need them." },
  { icon: Sparkles, title: "Versioned exports", copy: "Download PDF, Word, Markdown, diagrams, OpenAPI, and a complete ZIP package." },
];

export default function FeaturesPage() {
  return (
    <PageTransition>
      <section className="public-page">
        <section className="public-hero public-hero--visual public-hero--compact">
          <div className="public-hero__content"><p className="public-eyebrow"><Sparkles className="h-4 w-4" /> Product capabilities</p><h1>One workspace for the whole documentation flow.</h1><p className="public-hero__copy">Every capability lives in the same project page, so the path from idea to usable documents stays obvious and fast.</p></div>
          <SignalForgeVisual name="intelligence" label="System intelligence map" />
        </section>

        <StaggerContainer className="public-grid public-grid--six mt-6">
          {features.map(({ icon: Icon, title, copy }) => <StaggerItem key={title}><Card hover className="public-card"><span className="public-card__icon"><Icon className="h-5 w-5" /></span><h2>{title}</h2><p>{copy}</p></Card></StaggerItem>)}
        </StaggerContainer>

        <FadeIn className="mt-6 sf-visual-frame"><div className="sf-visual-frame__header"><p>Shared source of truth</p><span>Evidence → requirements → exports</span></div><SignalForgeVisual name="traceability" label="Traceability evidence map" /></FadeIn>
      </section>
    </PageTransition>
  );
}
