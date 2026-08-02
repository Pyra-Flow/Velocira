"use client";

import { useEffect } from "react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import SignalForgeVisual from "@/components/ui/SignalForgeVisual";

export default function Error({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    // Keep the user-facing error generic; details belong in observability tooling.
  }, []);

  return (
    <section className="sf-system-state">
      <Card className="sf-system-state__panel text-center">
        <p className="sf-meta text-accent">SYSTEM STATE / ERROR</p>
        <h1 className="mt-3 text-xl font-semibold text-foreground">Something went wrong</h1>
        <p className="mt-2 text-sm text-foreground-secondary" role="alert">
          The page could not be loaded. Please try again.
        </p>
        <SignalForgeVisual name="documents" decorative className="sf-system-state__visual" />
        <div className="mt-6"><Button onClick={reset}>Try again</Button></div>
      </Card>
    </section>
  );
}
