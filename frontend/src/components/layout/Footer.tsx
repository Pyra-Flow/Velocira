import Link from "next/link";
import { FadeIn } from "@/components/ui/Animations";
import VelociraLogo from "@/components/branding/VelociraLogo";

export default function Footer() {
  return (
    <FadeIn>
      <footer className="border-t border-border bg-card/50">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-3 px-4 py-8 text-center sm:flex-row sm:px-6 sm:text-start lg:px-8">
          <Link href="/" className="flex items-center gap-2"><span className="flex h-8 w-8 items-center justify-center rounded-md border border-border bg-background-secondary"><VelociraLogo size={26} /></span><span className="font-semibold font-display text-foreground">Velocira</span></Link>
          <p className="text-sm text-foreground-secondary">Pre-release workspace · no public service commitments are currently published.</p>
          <div className="flex gap-4 text-sm"><Link href="/privacy" className="text-foreground-secondary hover:text-accent">Privacy</Link><Link href="/terms" className="text-foreground-secondary hover:text-accent">Terms</Link></div>
        </div>
      </footer>
    </FadeIn>
  );
}
