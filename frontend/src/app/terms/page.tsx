"use client";

import Link from "next/link";
import {
  Scale,
  FileText,
  Shield,
  AlertTriangle,
  Users,
  CreditCard,
  Gavel,
  Ban,
  Key,
  Brain,
  Mail,
  ScrollText,
} from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import { FadeIn, StaggerContainer, StaggerItem, PageTransition } from "@/components/ui/Animations";

/* ------------------------------------------------------------------ */
/*  Data                                                               */
/* ------------------------------------------------------------------ */

const sections = [
  {
    id: "acceptance",
    icon: ScrollText,
    title: "Acceptance of Terms",
    content: [
      "By accessing or using the Velocira platform (\"Service\"), you agree to be bound by these Terms of Service (\"Terms\"). If you do not agree to these Terms, you may not access or use the Service. These Terms constitute a legally binding agreement between you and Velocira (\"Company\", \"we\", \"us\", or \"our\").",
      "We reserve the right to modify these Terms at any time. Material changes will be communicated via email or a prominent notice on the platform at least 30 days before they take effect. Your continued use of the Service after such changes constitutes acceptance of the updated Terms.",
      "If you are using the Service on behalf of an organization, you represent and warrant that you have the authority to bind that organization to these Terms, and \"you\" will refer to both you individually and the organization.",
    ],
  },
  {
    id: "description",
    icon: FileText,
    title: "Description of Service",
    content: [
      "Velocira is an AI-powered documentation platform that enables users to generate, edit, manage, and export software documentation including Software Requirements Specifications (SRS), use case diagrams, entity-relationship diagrams (ERDs), API structures, architecture proposals, and implementation roadmaps.",
      "The Service is provided on an \"as-is\" and \"as-available\" basis. We continuously improve the platform and may add, modify, or remove features at our discretion. We will make reasonable efforts to notify users of significant changes that may affect their workflows.",
      "AI-generated content is produced by machine learning models and should be reviewed by qualified professionals before use in production environments. Velocira does not guarantee the accuracy, completeness, or fitness for any particular purpose of AI-generated documentation.",
    ],
  },
  {
    id: "accounts",
    icon: Key,
    title: "User Accounts",
    content: [
      "To access most features of Velocira, you must create an account by providing a valid email address and creating a secure password. You are responsible for maintaining the confidentiality of your account credentials and for all activities that occur under your account.",
      "You must provide accurate, current, and complete information during registration and keep your account information updated. You agree to notify us immediately of any unauthorized access to or use of your account by contacting support@velocira.dev.",
      "We reserve the right to suspend or terminate accounts that violate these Terms, remain inactive for more than 24 months, or are associated with fraudulent activity. You may delete your account at any time through your account settings.",
      "Each account is intended for use by a single individual. Sharing account credentials with multiple users is prohibited unless you are on a Team or Enterprise plan with designated seat-based access.",
    ],
  },
  {
    id: "acceptable-use",
    icon: Shield,
    title: "Acceptable Use Policy",
    content: [
      "You agree to use Velocira only for lawful purposes and in compliance with all applicable local, state, national, and international laws and regulations. You may not use the Service to generate documentation for illegal activities or systems designed to cause harm.",
      "You may not: (a) attempt to gain unauthorized access to our systems or other users' accounts; (b) use automated scripts, bots, or scrapers to access the Service beyond normal API usage; (c) reverse-engineer, decompile, or disassemble any part of the platform; (d) upload malicious code, viruses, or harmful content.",
      "You may not use the Service to generate content that is defamatory, obscene, fraudulent, or infringes on the intellectual property rights of others. We reserve the right to remove content and suspend accounts that violate this policy without prior notice.",
      "Rate limits and fair usage policies apply to all plans. Excessive usage that degrades the Service for other users may result in temporary throttling or account suspension. Specific limits are detailed in your plan documentation.",
    ],
  },
  {
    id: "intellectual-property",
    icon: Scale,
    title: "Intellectual Property",
    content: [
      "The Velocira platform, including its user interface, code, algorithms, AI models, documentation, logos, trademarks, and all related intellectual property, is owned by Velocira and protected by international copyright, trademark, and patent laws.",
      "You may not copy, modify, distribute, sell, or lease any part of the Velocira platform or its underlying technology without our explicit written permission. The Velocira name, logo, and all related names and slogans are trademarks of the Company.",
      "We respect the intellectual property rights of others. If you believe that content on our platform infringes your copyright, please submit a DMCA takedown notice to legal@velocira.dev with the required information as specified under the Digital Millennium Copyright Act.",
    ],
  },
  {
    id: "generated-content",
    icon: Brain,
    title: "Generated Content Ownership",
    content: [
      "You retain ownership of all input content you provide to Velocira, including project descriptions, prompts, and any text you manually enter or edit within the platform.",
      "Documentation generated by Velocira's AI based on your input is owned by you, subject to the following conditions: (a) the generated content may contain patterns and structures common to AI outputs and cannot be claimed as entirely unique; (b) similar inputs from different users may produce similar outputs.",
      "You grant Velocira a non-exclusive, worldwide, royalty-free license to use anonymized and aggregated versions of your inputs and outputs solely for the purpose of improving our AI models and platform, unless you opt out via your account settings.",
      "We do not claim ownership over your generated documentation. You are free to use, modify, distribute, and commercialize your generated content without restriction. However, you are solely responsible for reviewing and validating generated content before use.",
    ],
  },
  {
    id: "payment",
    icon: CreditCard,
    title: "Payment Terms",
    content: [
      "Velocira offers free and paid subscription plans. Paid plans are billed on a monthly or annual basis as selected during checkout. All prices are displayed in US Dollars unless otherwise specified, and are exclusive of applicable taxes.",
      "Payment is processed securely through Stripe. By subscribing to a paid plan, you authorize us to charge your payment method on a recurring basis until you cancel. You may cancel your subscription at any time, and you will retain access to paid features until the end of your current billing period.",
      "We do not offer refunds for partial billing periods. If you downgrade from a paid plan to the free tier, you will retain paid features until your current billing cycle ends, after which your account will transition to the free plan with its associated limitations.",
      "We reserve the right to change our pricing with 30 days' advance notice. Price changes will take effect at the start of your next billing cycle. If you do not agree with a price change, you may cancel your subscription before the new pricing takes effect.",
    ],
  },
  {
    id: "liability",
    icon: AlertTriangle,
    title: "Limitation of Liability",
    content: [
      "To the maximum extent permitted by applicable law, Velocira and its officers, directors, employees, and agents shall not be liable for any indirect, incidental, special, consequential, or punitive damages, including but not limited to loss of profits, data, business opportunities, or goodwill.",
      "Our total aggregate liability to you for any claims arising from or related to the Service shall not exceed the amount you paid to Velocira in the twelve (12) months preceding the claim. If you are using the free tier, our maximum liability shall not exceed $50 USD.",
      "AI-generated content is provided for informational purposes and should not be relied upon as professional advice. Velocira is not liable for any decisions made or actions taken based on AI-generated documentation. You are responsible for verifying the accuracy and suitability of all generated content.",
      "These limitations apply regardless of the theory of liability (contract, tort, negligence, strict liability, or otherwise) and even if Velocira has been advised of the possibility of such damages.",
    ],
  },
  {
    id: "termination",
    icon: Ban,
    title: "Termination",
    content: [
      "You may terminate your account at any time by navigating to your account settings and selecting \"Delete Account.\" Upon termination, your data will be permanently deleted within 30 days, except as required by law or described in our Privacy Policy.",
      "We may suspend or terminate your account immediately, without prior notice, if we reasonably believe you have violated these Terms, engaged in fraudulent activity, or posed a security risk to the platform or other users.",
      "Upon termination, your right to access the Service ceases immediately. Any provisions of these Terms that by their nature should survive termination will remain in effect, including intellectual property rights, limitations of liability, and dispute resolution provisions.",
      "If your account is terminated due to a violation of these Terms, you may not create a new account without our written permission.",
    ],
  },
  {
    id: "governing-law",
    icon: Gavel,
    title: "Governing Law & Dispute Resolution",
    content: [
      "These Terms shall be governed by and construed in accordance with the laws of the Arab Republic of Egypt, without regard to conflict of law principles. Any disputes arising from these Terms or the use of the Service shall be resolved exclusively in the courts of Cairo, Egypt.",
      "Before initiating formal legal proceedings, you agree to attempt to resolve disputes informally by contacting us at legal@velocira.dev. We will work in good faith to address your concerns within 30 days.",
      "For EU residents, you may also be entitled to submit disputes to the European Commission's Online Dispute Resolution (ODR) platform at https://ec.europa.eu/odr. This does not affect your statutory rights under applicable consumer protection laws.",
    ],
  },
  {
    id: "contact",
    icon: Mail,
    title: "Contact Information",
    content: [
      "If you have any questions or concerns about these Terms of Service, please contact us through the following channels:",
      "General Inquiries: support@velocira.dev | Legal Department: legal@velocira.dev | Privacy Concerns: privacy@velocira.dev",
      "We aim to respond to all inquiries within 48 business hours. For urgent matters related to account security, please include \"URGENT\" in the subject line of your email.",
    ],
  },
];

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function TermsPage() {
  return (
    <PageTransition>
      {/* ───────── Hero ───────── */}
      <section className="relative overflow-hidden pt-32 pb-20 sm:pt-40 sm:pb-28">
        

        {/* Geometric accents */}
        <div className="absolute top-24 right-[15%] w-5 h-5 border border-primary/[0.12] rotate-45 hidden sm:block" />
        <div className="absolute bottom-16 left-[12%] w-3 h-3 border border-primary/[0.08] rotate-45 hidden sm:block" />
        <div className="absolute top-1/3 right-[8%] w-16 h-[1px] bg-gradient-to-r from-primary/[0.12] to-transparent hidden lg:block" />

        <div className="relative max-w-4xl mx-auto px-4 text-center">
          <FadeIn>
            <div className="inline-flex items-center gap-2 px-5 py-2 rounded-full border border-primary/20 bg-primary/[0.06] text-primary text-sm font-medium mb-8">
              <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
              Legal Agreement
            </div>

            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold font-display text-foreground">
              Terms of <span className="gradient-text">Service</span>
            </h1>

            <p className="mt-6 text-lg text-foreground-secondary max-w-2xl mx-auto leading-relaxed">
              Please read these terms carefully before using Velocira. By accessing our platform,
              you agree to be bound by these terms and conditions.
            </p>

            <p className="mt-4 text-sm text-foreground-secondary/60">
              Effective Date: February 1, 2026
            </p>
          </FadeIn>
        </div>
      </section>

      {/* ───────── Table of Contents ───────── */}
      <section className="max-w-4xl mx-auto px-4 pb-12">
        <FadeIn delay={0.1}>
          <Card className="p-8">
            <h2 className="text-lg font-semibold text-foreground mb-4 font-display">Table of Contents</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              {sections.map((section, i) => (
                <a
                  key={section.id}
                  href={`#${section.id}`}
                  className="flex items-center gap-2 text-sm text-foreground-secondary hover:text-primary transition-colors py-1"
                >
                  <span className="text-primary/60 font-mono text-xs">{String(i + 1).padStart(2, "0")}</span>
                  {section.title}
                </a>
              ))}
            </div>
          </Card>
        </FadeIn>
      </section>

      {/* ───────── Terms Sections ───────── */}
      <section className="max-w-4xl mx-auto px-4 pb-24">
        <StaggerContainer className="space-y-8">
          {sections.map((section, index) => {
            const Icon = section.icon;
            return (
              <StaggerItem key={section.id}>
                <Card id={section.id} className="scroll-mt-24">
                  <div className="flex items-start gap-4 mb-6">
                    <div className="flex-shrink-0 p-3 rounded-xl bg-primary/10">
                      <Icon className="h-6 w-6 text-primary" />
                    </div>
                    <div>
                      <span className="text-xs font-mono text-foreground-secondary/60 block mb-1">
                        Section {String(index + 1).padStart(2, "0")}
                      </span>
                      <h2 className="text-xl sm:text-2xl font-bold font-display text-foreground">
                        {section.title}
                      </h2>
                    </div>
                  </div>

                  <div className="space-y-4 ps-0 sm:ps-16">
                    {section.content.map((paragraph, pIndex) => (
                      <p key={pIndex} className="text-foreground-secondary leading-relaxed text-sm sm:text-base">
                        {paragraph}
                      </p>
                    ))}
                  </div>
                </Card>
              </StaggerItem>
            );
          })}
        </StaggerContainer>
      </section>

      {/* ───────── Bottom CTA ───────── */}
      <section className="relative overflow-hidden py-20 sm:py-28">
        <div className="relative max-w-3xl mx-auto px-4 text-center">
          <FadeIn>
            <h2 className="text-2xl sm:text-3xl font-bold font-display text-foreground">
              Have Questions About Our <span className="gradient-text">Terms</span>?
            </h2>
            <p className="mt-4 text-foreground-secondary max-w-xl mx-auto">
              Our legal team is here to help clarify any questions you may have about these terms.
            </p>
            <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
              <Link href="/contact">
                <Button size="lg" icon={<Mail className="h-5 w-5" />}>
                  Contact Legal Team
                </Button>
              </Link>
              <Link href="/privacy">
                <Button variant="outline" size="lg" icon={<Shield className="h-5 w-5" />}>
                  Privacy Policy
                </Button>
              </Link>
            </div>
          </FadeIn>
        </div>
      </section>
    </PageTransition>
  );
}
