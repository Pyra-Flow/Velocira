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
        <Link href="/" className="auth-page__intro-brand" aria-label="Velocira home">
          <span><VelociraLogo size={30} priority /></span>
          Velocira
        </Link>
        <div className="auth-page__intro-copy">
          <p>Documentation, without the maze</p>
          <h2>Start with what you know. We&apos;ll make it clear.</h2>
          <span>Answer focused questions, review the brief, then generate the documents and diagrams your team needs.</span>
          <FadeIn className="mt-7">
            <SignalForgeVisual
              name="auth"
              priority
              label="Protected documentation flow"
              className="min-h-[156px]"
            />
          </FadeIn>
        </div>
        <ol className="auth-page__intro-steps">
          <li><ClipboardList aria-hidden="true" /><span><strong>Answer questions</strong><small>One useful prompt at a time</small></span></li>
          <li><FileCheck2 aria-hidden="true" /><span><strong>Check the brief</strong><small>Keep assumptions visible</small></span></li>
          <li><Network aria-hidden="true" /><span><strong>Generate outputs</strong><small>Docs and diagrams stay connected</small></span></li>
        </ol>
      </aside>
      <section className="auth-page__panel" aria-labelledby="auth-page-title">
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
      </section>
    </div>
  );
}
