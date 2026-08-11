import Link from "next/link";
import { ClipboardList, FileCheck2, Network, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import VelociraLogo from "@/components/branding/VelociraLogo";
import SignalForgeVisual from "@/components/ui/SignalForgeVisual";
import { FadeIn } from "@/components/ui/Animations";

type Props = {
  eyebrow: string;
  title: string;
  description: string;
  icon: LucideIcon;
  children: ReactNode;
  footer?: ReactNode;
};

export default function AuthPageFrame({ eyebrow, title, description, icon: Icon, children, footer }: Props) {
  return (
    <div className="auth-page">
      <aside className="auth-page__intro" aria-label="How Velocira works">
        <p className="auth-page__system-label">SYS://AUTH/01</p>
        <Link href="/" className="auth-page__intro-brand" aria-label="Velocira home">
          <span><VelociraLogo size={30} priority /></span>
          Velocira
        </Link>
        <div className="auth-page__intro-copy">
          <p>Connected documentation workspace</p>
          <h2>Precision in motion. Confidence in every decision.</h2>
          <span>Velocira connects the project brief, supporting evidence, requirements, and generated documentation so teams can move without losing the why.</span>
          <FadeIn className="mt-7">
            <SignalForgeVisual
              name="auth"
              priority
              label="Protected documentation flow"
              className="min-h-[156px]"
            />
          </FadeIn>
        </div>
        <ol className="auth-page__intro-steps" aria-label="Documentation flow">
          <li><ClipboardList aria-hidden="true" /><span><strong>Capture intent</strong><small>Start with what the team knows</small></span></li>
          <li><FileCheck2 aria-hidden="true" /><span><strong>Keep evidence visible</strong><small>Review assumptions in context</small></span></li>
          <li><Network aria-hidden="true" /><span><strong>Generate with traceability</strong><small>Documents remain connected</small></span></li>
        </ol>
      </aside>
      <section className="auth-page__panel" aria-labelledby="auth-page-title">
        <p className="auth-page__panel-system">AUTH://{eyebrow.toUpperCase().replaceAll(" ", "-")}</p>
        <Link href="/" className="auth-page__brand" aria-label="Velocira home">
          <span><VelociraLogo size={30} priority /></span>
          Velocira
        </Link>
        <FadeIn className="auth-page__heading">
          <div className="auth-page__icon"><Icon aria-hidden="true" /></div>
          <p>{eyebrow}</p>
          <h1 id="auth-page-title">{title}</h1>
          <p className="auth-page__description">{description}</p>
        </FadeIn>
        {children}
        {footer && <div className="auth-page__footer">{footer}</div>}
        <ol className="auth-page__protocol" aria-label="Authentication protocol">
          <li className="is-current"><span>01</span>Sign in</li>
          <li><span>02</span>Verify</li>
          <li><span>03</span>Reset</li>
          <li><span>04</span>Set new</li>
        </ol>
      </section>
    </div>
  );
}
