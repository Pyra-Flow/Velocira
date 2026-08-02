import VelociraLogo from "@/components/branding/VelociraLogo";
import SignalForgeVisual from "@/components/ui/SignalForgeVisual";

export default function Loading() {
  return (
    <section className="sf-system-state" aria-busy="true" aria-live="polite">
      <section className="sf-system-state__panel text-center">
        <span className="sf-global-error__logo mx-auto"><VelociraLogo size={40} priority /></span>
        <p className="sf-meta mt-5 text-accent">SYSTEM STATE / LOADING</p>
        <h1 className="mt-3 text-xl">Preparing your workspace</h1>
        <p className="mt-2 text-sm text-foreground-secondary">Loading current project context and controls.</p>
        <SignalForgeVisual name="intelligence" decorative className="sf-system-state__visual" />
        <div className="mx-auto mt-6 h-1 w-36 overflow-hidden rounded-full bg-background-secondary">
          <span className="block h-full w-1/2 animate-pulse bg-accent" />
        </div>
      </section>
    </section>
  );
}
