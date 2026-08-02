import Link from "next/link";
import { ArrowRight, Check, Sparkles } from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import SignalForgeVisual from "@/components/ui/SignalForgeVisual";
import { FadeIn, PageTransition } from "@/components/ui/Animations";

export default function PricingPage() {
  return <PageTransition><section className="public-page"><section className="public-hero public-hero--visual public-hero--compact"><div className="public-hero__content"><p className="public-eyebrow"><Sparkles className="h-4 w-4" /> Product access</p><h1>Start with the complete workflow.</h1><p className="public-hero__copy">Velocira is currently being refined with early users. Published subscriptions are not available yet, but the project workspace is designed as one straightforward, end-to-end experience.</p></div><SignalForgeVisual name="intelligence" priority label="Documentation system intelligence" /></section><FadeIn className="mt-6"><Card className="max-w-2xl p-6 sm:p-8"><p className="sf-meta text-accent">Early access</p><h2 className="mt-2 text-2xl font-semibold tracking-tight text-foreground">Everything in one workspace</h2><ul className="mt-6 space-y-3 text-sm text-foreground-secondary">{["Adaptive discovery questions", "SRS and linked documentation package", "PDF, Word, Markdown, diagram, and OpenAPI exports"].map((item) => <li key={item} className="flex gap-3"><Check className="mt-0.5 h-4 w-4 shrink-0 text-success" />{item}</li>)}</ul><Link href="/register" className="mt-8 inline-block"><Button icon={<ArrowRight className="h-4 w-4" />}>Create an account</Button></Link></Card></FadeIn></section></PageTransition>;
}
