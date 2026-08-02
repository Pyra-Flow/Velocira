"use client";

import "./globals.css";
import VelociraLogo from "@/components/branding/VelociraLogo";
import SignalForgeVisual from "@/components/ui/SignalForgeVisual";

export default function GlobalError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <html lang="en" className="dark">
      <body className="sf-global-error">
        <main className="sf-system-state">
          <section className="sf-system-state__panel" aria-labelledby="global-error-title">
            <span className="sf-global-error__logo"><VelociraLogo size={42} priority /></span>
            <p className="sf-meta mt-5 text-accent">SYSTEM STATE / UNAVAILABLE</p>
            <h1 id="global-error-title" className="mt-3 text-2xl">Velocira could not load</h1>
            <p className="mt-2 text-sm text-foreground-secondary" role="alert">Please retry. If the problem continues, return to the dashboard later.</p>
            <SignalForgeVisual name="documents" decorative className="sf-system-state__visual" />
            <button className="sf-global-error__button" type="button" onClick={reset}>Try again</button>
          </section>
        </main>
      </body>
    </html>
  );
}
