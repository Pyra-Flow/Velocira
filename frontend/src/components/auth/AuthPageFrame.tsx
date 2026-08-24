import Link from "next/link";
import { CheckCircle2, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import VelociraLogo from "@/components/branding/VelociraLogo";
import { FadeIn } from "@/components/ui/Animations";

type Props = {
  eyebrow: string;
  title: string;
  description: string;
  icon: LucideIcon;
  children: ReactNode;
  footer?: ReactNode;
};

const nextSteps = [
  "Describe your product in a sentence or two",
  "Review a short project briefing",
  "Answer focused questions at your own pace",
];

export default function AuthPageFrame({ eyebrow, title, description, icon: Icon, children, footer }: Props) {
  return (
    <div className="auth-page auth-page--focused">
      <aside className="auth-page__intro auth-page__intro--focused" aria-label="What happens after sign in">
        <Link href="/" className="auth-page__intro-brand" aria-label="Velocira home">
          <span><VelociraLogo size={30} priority /></span>
          Velocira
        </Link>
        <div className="auth-page__intro-copy auth-page__intro-copy--focused">
          <p>Start in a few minutes</p>
          <h2>Turn product context into decisions your team can review.</h2>
          <span>After you sign in, you’ll create a project, see what to prepare, and work through one useful question at a time.</span>
        </div>
        <ol className="auth-next-steps">
          {nextSteps.map((step) => <li key={step}><CheckCircle2 aria-hidden="true" /><span>{step}</span></li>)}
        </ol>
      </aside>
      <section className="auth-page__panel auth-page__panel--focused" aria-labelledby="auth-page-title">
        <Link href="/" className="auth-page__brand" aria-label="Velocira home">
          <span><VelociraLogo size={30} priority /></span>
          Velocira
        </Link>
        <FadeIn className="auth-page__heading auth-page__heading--focused">
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
