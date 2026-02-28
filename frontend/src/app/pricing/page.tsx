"use client";

import { useState } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import {
  Check,
  X,
  Zap,
  Crown,
  Building2,
  ChevronDown,
  ArrowRight,
  Sparkles,
  Shield,
  Users,
  Headphones,
} from "lucide-react";
import { useLocale } from "@/providers/LocaleProvider";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import {
  FadeIn,
  StaggerContainer,
  StaggerItem,
  PageTransition,
} from "@/components/ui/Animations";

/* ------------------------------------------------------------------ */
/*  Types & Data                                                       */
/* ------------------------------------------------------------------ */

interface PricingTier {
  name: string;
  description: string;
  monthlyPrice: number | null;
  annualPrice: number | null;
  priceLabel?: string;
  icon: React.ElementType;
  color: string;
  bgColor: string;
  popular?: boolean;
  cta: string;
  ctaVariant: "primary" | "secondary" | "outline";
  features: string[];
}

const tiers: PricingTier[] = [
  {
    name: "Free",
    description: "Perfect for individuals exploring AI documentation.",
    monthlyPrice: 0,
    annualPrice: 0,
    icon: Zap,
    color: "text-foreground",
    bgColor: "bg-background-secondary",
    cta: "Get Started Free",
    ctaVariant: "outline",
    features: [
      "Up to 3 projects",
      "Basic SRS generation",
      "PDF export",
      "Community support",
      "1 team member",
    ],
  },
  {
    name: "Pro",
    description: "For professionals who need full documentation power.",
    monthlyPrice: 12,
    annualPrice: 9,
    icon: Crown,
    color: "text-primary",
    bgColor: "bg-primary/10",
    popular: true,
    cta: "Start Pro Trial",
    ctaVariant: "primary",
    features: [
      "Unlimited projects",
      "All document types (SRS, ERD, API, Use Cases, Architecture)",
      "PDF, DOCX & Markdown export",
      "Priority support",
      "Up to 5 team members",
      "Section regeneration with prompts",
      "Custom templates",
    ],
  },
  {
    name: "Enterprise",
    description: "For teams that need security, scale & dedicated support.",
    monthlyPrice: null,
    annualPrice: null,
    priceLabel: "Custom",
    icon: Building2,
    color: "text-accent",
    bgColor: "bg-accent/10",
    cta: "Contact Sales",
    ctaVariant: "secondary",
    features: [
      "Everything in Pro",
      "Unlimited team members",
      "SSO / SAML authentication",
      "Dedicated account manager",
      "Custom integrations & API access",
      "SLA guarantee (99.9% uptime)",
      "Admin analytics dashboard",
    ],
  },
];

interface ComparisonRow {
  feature: string;
  free: boolean | string;
  pro: boolean | string;
  enterprise: boolean | string;
}

const comparisonData: ComparisonRow[] = [
  { feature: "Projects", free: "3", pro: "Unlimited", enterprise: "Unlimited" },
  { feature: "SRS Generation", free: true, pro: true, enterprise: true },
  { feature: "ERD & API Docs", free: false, pro: true, enterprise: true },
  { feature: "Use Cases & Architecture", free: false, pro: true, enterprise: true },
  { feature: "PDF Export", free: true, pro: true, enterprise: true },
  { feature: "DOCX & Markdown Export", free: false, pro: true, enterprise: true },
  { feature: "Section Regeneration", free: false, pro: true, enterprise: true },
  { feature: "Custom Templates", free: false, pro: true, enterprise: true },
  { feature: "Team Members", free: "1", pro: "Up to 5", enterprise: "Unlimited" },
  { feature: "SSO / SAML", free: false, pro: false, enterprise: true },
  { feature: "Custom Integrations", free: false, pro: false, enterprise: true },
  { feature: "SLA Guarantee", free: false, pro: false, enterprise: true },
  { feature: "Admin Analytics", free: false, pro: false, enterprise: true },
  { feature: "Support", free: "Community", pro: "Priority", enterprise: "Dedicated" },
];

interface FaqItem {
  question: string;
  answer: string;
}

const faqs: FaqItem[] = [
  {
    question: "Can I switch plans at any time?",
    answer:
      "Absolutely. You can upgrade or downgrade your plan at any time from your account settings. When upgrading, you'll be charged a prorated amount for the remainder of the billing period. Downgrading takes effect at the next billing cycle.",
  },
  {
    question: "Is there a free trial for the Pro plan?",
    answer:
      "Yes! Every new account starts with a 14-day free trial of the Pro plan — no credit card required. At the end of the trial, you can choose to continue with Pro or switch to the Free tier.",
  },
  {
    question: "What happens to my projects if I downgrade?",
    answer:
      "Your existing projects remain safe and accessible, but you won't be able to create new projects beyond the Free tier's 3-project limit. You can still view and export all your documents.",
  },
  {
    question: "Do you offer discounts for startups or nonprofits?",
    answer:
      "Yes, we offer special pricing for verified startups (under 2 years old) and registered nonprofits. Contact our sales team at sales@velocira.io to learn more about eligibility.",
  },
  {
    question: "How does annual billing work?",
    answer:
      "Annual billing is charged once per year at the discounted rate shown. That's $108/year for Pro (saving $36 compared to monthly). You can cancel anytime — unused months will be refunded.",
  },
  {
    question: "What payment methods do you accept?",
    answer:
      "We accept all major credit cards (Visa, Mastercard, Amex), PayPal, and bank transfers for Enterprise plans. All payments are processed securely through Stripe.",
  },
];

/* ------------------------------------------------------------------ */
/*  Sub-components                                                     */
/* ------------------------------------------------------------------ */

function BillingToggle({
  isAnnual,
  onToggle,
}: {
  isAnnual: boolean;
  onToggle: () => void;
}) {
  return (
    <FadeIn delay={0.2}>
      <div className="flex items-center justify-center gap-4 mt-8">
        <span
          className={`text-sm font-medium transition-colors ${
            !isAnnual ? "text-foreground" : "text-foreground-secondary"
          }`}
        >
          Monthly
        </span>
        <button
          onClick={onToggle}
          className="relative w-14 h-7 rounded-full bg-background-secondary border border-border transition-colors cursor-pointer focus-ring"
          aria-label="Toggle annual billing"
        >
          <motion.div
            className="absolute top-0.5 w-6 h-6 rounded-full bg-primary shadow-md"
            animate={{ left: isAnnual ? "calc(100% - 1.625rem)" : "0.125rem" }}
            transition={{ type: "spring", stiffness: 500, damping: 30 }}
          />
        </button>
        <span
          className={`text-sm font-medium transition-colors ${
            isAnnual ? "text-foreground" : "text-foreground-secondary"
          }`}
        >
          Annual
        </span>
        <AnimatePresence>
          {isAnnual && (
            <motion.span
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.8 }}
              className="text-xs font-semibold text-primary bg-primary/10 px-2.5 py-1 rounded-full"
            >
              Save 25%
            </motion.span>
          )}
        </AnimatePresence>
      </div>
    </FadeIn>
  );
}

function PricingCard({
  tier,
  isAnnual,
}: {
  tier: PricingTier;
  isAnnual: boolean;
}) {
  const Icon = tier.icon;
  const displayPrice =
    tier.monthlyPrice !== null
      ? isAnnual
        ? tier.annualPrice
        : tier.monthlyPrice
      : null;

  return (
    <Card
      hover
      glow={tier.popular}
      className={`relative flex flex-col h-full ${
        tier.popular ? "border-primary/50" : ""
      }`}
    >
      {tier.popular && (
        <div className="absolute -top-3.5 left-1/2 -translate-x-1/2">
          <span className="bg-primary text-on-primary text-xs font-bold px-4 py-1.5 rounded-full flex items-center gap-1.5">
            <Sparkles className="w-3.5 h-3.5" />
            Most Popular
          </span>
        </div>
      )}

      <div className="flex items-center gap-3 mb-4">
        <div className={`p-2.5 rounded-xl ${tier.bgColor}`}>
          <Icon className={`w-5 h-5 ${tier.color}`} />
        </div>
        <div>
          <h3 className="text-lg font-bold text-foreground font-display">
            {tier.name}
          </h3>
        </div>
      </div>

      <p className="text-sm text-foreground-secondary mb-6">
        {tier.description}
      </p>

      <div className="mb-6">
        {displayPrice !== null ? (
          <div className="flex items-baseline gap-1">
            <span className="text-4xl font-bold text-foreground font-display">
              ${displayPrice}
            </span>
            <span className="text-foreground-secondary text-sm">/month</span>
          </div>
        ) : (
          <div className="flex items-baseline gap-1">
            <span className="text-4xl font-bold text-foreground font-display">
              {tier.priceLabel}
            </span>
          </div>
        )}
        {isAnnual && tier.monthlyPrice !== null && tier.monthlyPrice > 0 && (
          <motion.p
            initial={{ opacity: 0, y: -5 }}
            animate={{ opacity: 1, y: 0 }}
            className="text-xs text-foreground-secondary mt-1"
          >
            <span className="line-through">${tier.monthlyPrice}/mo</span>{" "}
            <span className="text-primary font-medium">
              billed ${(tier.annualPrice ?? 0) * 12}/year
            </span>
          </motion.p>
        )}
      </div>

      <div className="border-t border-border pt-5 flex-1">
        <p className="text-xs font-semibold text-foreground-secondary uppercase tracking-wider mb-3">
          What&apos;s included
        </p>
        <ul className="space-y-2.5">
          {tier.features.map((feature) => (
            <li key={feature} className="flex items-start gap-2.5 text-sm">
              <Check className={`w-4 h-4 mt-0.5 shrink-0 ${tier.color}`} />
              <span className="text-foreground-secondary">{feature}</span>
            </li>
          ))}
        </ul>
      </div>

      <div className="mt-8">
        <Link href={tier.name === "Enterprise" ? "/contact" : "/register"}>
          <Button
            variant={tier.ctaVariant}
            size="lg"
            className="w-full"
            icon={tier.name === "Enterprise" ? <Headphones className="w-4 h-4" /> : undefined}
          >
            {tier.cta}
          </Button>
        </Link>
      </div>
    </Card>
  );
}

function ComparisonCell({ value }: { value: boolean | string }) {
  if (typeof value === "string") {
    return (
      <span className="text-sm font-medium text-foreground">{value}</span>
    );
  }
  return value ? (
    <Check className="w-4.5 h-4.5 text-primary mx-auto" />
  ) : (
    <X className="w-4.5 h-4.5 text-foreground-secondary/40 mx-auto" />
  );
}

function FaqAccordion({ item }: { item: FaqItem }) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <div className="border border-border rounded-xl overflow-hidden transition-colors hover:border-border-hover">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="w-full flex items-center justify-between p-5 text-left cursor-pointer focus-ring"
      >
        <span className="font-medium text-foreground pr-4">
          {item.question}
        </span>
        <motion.div
          animate={{ rotate: isOpen ? 180 : 0 }}
          transition={{ duration: 0.2 }}
          className="shrink-0"
        >
          <ChevronDown className="w-5 h-5 text-foreground-secondary" />
        </motion.div>
      </button>
      <AnimatePresence initial={false}>
        {isOpen && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
          >
            <div className="px-5 pb-5 text-sm text-foreground-secondary leading-relaxed">
              {item.answer}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function PricingPage() {
  const { t } = useLocale();
  const [isAnnual, setIsAnnual] = useState(true);

  return (
    <PageTransition>
      <main className="min-h-screen">
        {/* ---- Hero ---- */}
        <section className="relative pt-32 pb-16 px-6 overflow-hidden">
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,var(--color-primary-light)_0%,transparent_60%)] opacity-30 pointer-events-none" />
        {/* Geometric accents */}
        <div className="absolute top-20 right-[10%] w-28 h-28 border border-primary/[0.06] rotate-45 pointer-events-none hidden lg:block" />
        <div className="absolute bottom-8 left-[8%] w-16 h-16 border border-primary/[0.05] rotate-45 pointer-events-none hidden lg:block" />

          <div className="relative max-w-4xl mx-auto text-center">
            <FadeIn>
              <span className="inline-flex items-center gap-2 px-5 py-2 rounded-full border border-primary/20 bg-primary/[0.06] text-primary text-sm font-medium mb-8">
                <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
                Pricing
              </span>
            </FadeIn>

            <FadeIn delay={0.1}>
              <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold font-display mb-6">
                <span className="text-foreground">Simple, </span>
                <span className="gradient-text">Transparent Pricing</span>
              </h1>
            </FadeIn>

            <FadeIn delay={0.15}>
              <p className="text-lg text-foreground-secondary max-w-2xl mx-auto">
                Start free and scale as you grow. No hidden fees, no surprises —
                just powerful AI documentation tools at every tier.
              </p>
            </FadeIn>

            <BillingToggle
              isAnnual={isAnnual}
              onToggle={() => setIsAnnual(!isAnnual)}
            />
          </div>
        </section>

        {/* ---- Pricing Cards ---- */}
        <section className="px-6 pb-24">
          <StaggerContainer className="max-w-6xl mx-auto grid grid-cols-1 md:grid-cols-3 gap-6 lg:gap-8 items-stretch">
            {tiers.map((tier) => (
              <StaggerItem key={tier.name} className="flex">
                <PricingCard tier={tier} isAnnual={isAnnual} />
              </StaggerItem>
            ))}
          </StaggerContainer>
        </section>

        {/* ---- Feature Comparison Table ---- */}
        <section className="px-6 pb-24">
          <div className="max-w-5xl mx-auto">
            <FadeIn>
              <h2 className="text-3xl font-bold font-display text-foreground text-center mb-4">
                Compare Plans
              </h2>
              <p className="text-foreground-secondary text-center mb-12 max-w-xl mx-auto">
                A detailed breakdown of everything included in each plan so you
                can pick the right fit.
              </p>
            </FadeIn>

            <FadeIn delay={0.1}>
              <Card className="overflow-hidden p-0">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-border">
                        <th className="text-left p-4 font-semibold text-foreground w-1/3">
                          Feature
                        </th>
                        <th className="text-center p-4 font-semibold text-foreground">
                          Free
                        </th>
                        <th className="text-center p-4 font-semibold text-primary">
                          Pro
                        </th>
                        <th className="text-center p-4 font-semibold text-foreground">
                          Enterprise
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {comparisonData.map((row, i) => (
                        <tr
                          key={row.feature}
                          className={`border-b border-border/50 transition-colors hover:bg-card/60 ${
                            i % 2 === 0 ? "" : "bg-card/30"
                          }`}
                        >
                          <td className="p-4 text-foreground-secondary font-medium">
                            {row.feature}
                          </td>
                          <td className="p-4 text-center">
                            <ComparisonCell value={row.free} />
                          </td>
                          <td className="p-4 text-center bg-primary/[0.03]">
                            <ComparisonCell value={row.pro} />
                          </td>
                          <td className="p-4 text-center">
                            <ComparisonCell value={row.enterprise} />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Card>
            </FadeIn>
          </div>
        </section>

        {/* ---- FAQ Section ---- */}
        <section className="px-6 pb-24">
          <div className="max-w-3xl mx-auto">
            <FadeIn>
              <h2 className="text-3xl font-bold font-display text-foreground text-center mb-4">
                Frequently Asked Questions
              </h2>
              <p className="text-foreground-secondary text-center mb-12 max-w-xl mx-auto">
                Everything you need to know about our pricing and plans.
              </p>
            </FadeIn>

            <StaggerContainer className="space-y-3">
              {faqs.map((faq) => (
                <StaggerItem key={faq.question}>
                  <FaqAccordion item={faq} />
                </StaggerItem>
              ))}
            </StaggerContainer>
          </div>
        </section>

        {/* ---- Enterprise CTA ---- */}
        <section className="px-6 pb-24">
          <FadeIn>
            <div className="max-w-5xl mx-auto">
              <Card className="glass p-8 md:p-12">
                <div className="flex flex-col md:flex-row items-center gap-8">
                  <div className="flex-1 text-center md:text-left">
                    <div className="flex items-center justify-center md:justify-start gap-3 mb-4">
                      <div className="p-2.5 rounded-xl bg-accent/10">
                        <Building2 className="w-6 h-6 text-accent" />
                      </div>
                      <h3 className="text-2xl font-bold font-display text-foreground">
                        Need Enterprise-Grade Power?
                      </h3>
                    </div>
                    <p className="text-foreground-secondary max-w-lg">
                      Get SSO, custom integrations, dedicated support, and SLA
                      guarantees tailored to your organization. Our team will
                      build a plan that fits your needs.
                    </p>
                    <div className="flex flex-wrap items-center justify-center md:justify-start gap-4 mt-6">
                      <div className="flex items-center gap-2 text-sm text-foreground-secondary">
                        <Shield className="w-4 h-4 text-primary" />
                        SOC 2 Compliant
                      </div>
                      <div className="flex items-center gap-2 text-sm text-foreground-secondary">
                        <Users className="w-4 h-4 text-primary" />
                        Unlimited Seats
                      </div>
                      <div className="flex items-center gap-2 text-sm text-foreground-secondary">
                        <Headphones className="w-4 h-4 text-primary" />
                        24/7 Support
                      </div>
                    </div>
                  </div>
                  <div className="shrink-0">
                    <Link href="/contact">
                      <Button
                        variant="primary"
                        size="lg"
                        icon={<ArrowRight className="w-4 h-4" />}
                      >
                        Talk to Sales
                      </Button>
                    </Link>
                  </div>
                </div>
              </Card>
            </div>
          </FadeIn>
        </section>

        {/* ---- Bottom CTA ---- */}
        <section className="px-6 pb-32">
          <FadeIn>
            <div className="max-w-3xl mx-auto text-center">
              <h2 className="text-3xl sm:text-4xl font-bold font-display mb-4">
                <span className="text-foreground">Ready to </span>
                <span className="gradient-text">Ship Docs Faster?</span>
              </h2>
              <p className="text-foreground-secondary text-lg mb-8 max-w-xl mx-auto">
                Join thousands of developers and teams who trust Velocira to
                generate production-ready documentation in seconds.
              </p>
              <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
                <Link href="/register">
                  <Button
                    variant="primary"
                    size="lg"
                    icon={<Sparkles className="w-4 h-4" />}
                    className=""
                  >
                    Start Free Today
                  </Button>
                </Link>
                <Link href="/features">
                  <Button
                    variant="ghost"
                    size="lg"
                    icon={<ArrowRight className="w-4 h-4" />}
                  >
                    See All Features
                  </Button>
                </Link>
              </div>
              <p className="text-xs text-foreground-secondary mt-4">
                No credit card required · Free forever on the starter plan
              </p>
            </div>
          </FadeIn>
        </section>
      </main>
    </PageTransition>
  );
}
