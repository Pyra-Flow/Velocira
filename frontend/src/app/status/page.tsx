import Link from "next/link";
import { Activity, ArrowRight, Wrench } from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import SignalForgeVisual from "@/components/ui/SignalForgeVisual";
import { PageTransition } from "@/components/ui/Animations";

export default function StatusPage() {
  return (
    <PageTransition>
      <section className="mx-auto max-w-3xl px-4 py-24 sm:px-6">
        <Card className="text-center">
          <Activity className="mx-auto h-9 w-9 text-accent" />
          <h1 className="mt-5 text-3xl font-bold font-display text-foreground">Service status is not published yet</h1>
          <p className="mx-auto mt-4 max-w-xl text-foreground-secondary">Velocira does not currently have a verified public monitoring feed. This page intentionally avoids showing estimated uptime, inferred service health, or fictional incident history.</p>
          <SignalForgeVisual name="intelligence" decorative className="mx-auto mt-6 max-w-xl" />
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Link href="/contact"><Button icon={<Wrench className="h-4 w-4" />}>Report an Issue</Button></Link>
            <Link href="/"><Button variant="outline" icon={<ArrowRight className="h-4 w-4" />}>Return Home</Button></Link>
          </div>
        </Card>
      </section>
    </PageTransition>
  );
}
