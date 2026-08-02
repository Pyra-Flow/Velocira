import Link from "next/link";
import { ArrowRight, Construction, FileText, LayoutDashboard } from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import SignalForgeVisual from "@/components/ui/SignalForgeVisual";
import { FadeIn, PageTransition } from "@/components/ui/Animations";

export default function PreReleasePage({ title, description }: { title: string; description: string }) {
  return (
    <PageTransition>
      <section className="mx-auto flex min-h-[68vh] max-w-5xl items-center px-4 py-20 sm:px-6">
        <Card className="relative w-full overflow-hidden px-6 py-10 sm:px-12 sm:py-12">
          <div className="absolute left-0 top-0 h-px w-16 bg-accent" />
          <div className="grid gap-10 text-left md:grid-cols-[1.1fr_.9fr] md:items-center">
            <FadeIn>
              <div className="flex h-14 w-14 items-center justify-center rounded-md border border-accent/25 bg-accent-light"><Construction className="h-7 w-7 text-accent" /></div>
              <p className="sf-meta mt-6 text-accent">Product update</p>
              <h1 className="mt-4 text-3xl font-bold font-display text-foreground sm:text-4xl">{title}</h1>
              <p className="mt-4 max-w-xl leading-7 text-foreground-secondary">{description}</p>
              <p className="mt-5 text-sm leading-6 text-foreground-secondary">This part of Velocira is being prepared with the same project data and account controls you already use. In the meantime, your dashboard and documents remain available.</p>
              <div className="mt-8 flex flex-wrap gap-3"><Link href="/dashboard"><Button icon={<LayoutDashboard className="h-4 w-4" />}>Open Dashboard</Button></Link><Link href="/docs"><Button variant="outline" icon={<FileText className="h-4 w-4" />}>Read Docs</Button></Link></div>
            </FadeIn>
            <FadeIn className="sf-visual-frame" delay={0.06}>
              <div className="sf-visual-frame__header"><p>Workspace continuity</p><span>Current flow</span></div>
              <SignalForgeVisual name="traceability" label="Connected project context" />
              <div className="p-5">
                <p className="sf-meta text-accent">What is available now</p>
                <ul className="mt-5 space-y-4 text-sm text-foreground-secondary">
                <li className="flex gap-3"><span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-success" />Project creation and guided interviews</li>
                <li className="flex gap-3"><span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-success" />Generated SRS and documentation packages</li>
                <li className="flex gap-3"><span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-success" />Account and workspace controls</li>
                </ul>
                <Link href="/" className="mt-7 inline-flex items-center gap-2 text-sm font-semibold text-accent transition-colors hover:text-foreground">Return Home <ArrowRight className="h-4 w-4" /></Link>
              </div>
            </FadeIn>
          </div>
        </Card>
      </section>
    </PageTransition>
  );
}
