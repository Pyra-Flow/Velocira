import Link from "next/link";
import { ArrowLeft, SearchX } from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import SignalForgeVisual from "@/components/ui/SignalForgeVisual";

export default function NotFound() {
  return (
    <section className="sf-system-state">
      <Card className="sf-system-state__panel text-center">
        <SearchX className="mx-auto h-9 w-9 text-accent" aria-hidden="true" />
        <p className="sf-meta mt-5 text-accent">SYSTEM STATE / NOT FOUND</p>
        <h1 className="mt-3 text-xl">This route is unavailable</h1>
        <p className="mt-2 text-sm text-foreground-secondary">The page may have moved, or you may not have access to its workspace.</p>
        <SignalForgeVisual name="traceability" decorative className="sf-system-state__visual" />
        <Link className="mt-6 inline-flex" href="/">
          <Button icon={<ArrowLeft className="h-4 w-4" />}>Return to Velocira</Button>
        </Link>
      </Card>
    </section>
  );
}
