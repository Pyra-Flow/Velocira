"use client";

import Link from "next/link";
import {
  Shield,
  Lock,
  Eye,
  Database,
  Cookie,
  Users,
  Mail,
  Clock,
  Baby,
  FileText,
  ArrowLeft,
} from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import { FadeIn, StaggerContainer, StaggerItem, PageTransition } from "@/components/ui/Animations";

/* ------------------------------------------------------------------ */
/*  Data                                                               */
/* ------------------------------------------------------------------ */

const sections = [
  {
    id: "information-we-collect",
    icon: Eye,
    title: "Information We Collect",
    content: [
      "We collect information you provide directly when you create an account, including your name, email address, and password. When you use our AI documentation generation services, we collect the project descriptions and prompts you submit.",
      "We automatically collect certain technical information when you access Velocira, including your IP address, browser type and version, operating system, device identifiers, referring URLs, and pages viewed. We use cookies and similar tracking technologies to gather this data.",
      "If you subscribe to a paid plan, our payment processor (Stripe) collects your billing information, including credit card details. We do not store full credit card numbers on our servers — only tokenized references provided by Stripe.",
      "We may also collect usage analytics such as feature usage frequency, document generation counts, export formats used, and session duration to improve our platform.",
    ],
  },
  {
    id: "how-we-use",
    icon: Database,
    title: "How We Use Your Information",
    content: [
      "We use your personal information to provide, maintain, and improve the Velocira platform, including generating AI-powered documentation based on your project descriptions and processing your transactions.",
      "Your data helps us personalize your experience, send you service-related notifications (such as email verification, password resets, and account alerts), and provide customer support when you reach out to us.",
      "We analyze aggregated, anonymized usage data to understand how our services are used, identify trends, and develop new features. We may use your prompts and generated documents in anonymized form to improve our AI models, unless you opt out in your account settings.",
      "We will never sell your personal information to third parties for marketing purposes. We may share data with service providers who help us operate our platform, but only under strict data processing agreements.",
    ],
  },
  {
    id: "data-storage",
    icon: Lock,
    title: "Data Storage & Security",
    content: [
      "Your data is stored on secure servers hosted on industry-leading cloud infrastructure providers (AWS and Google Cloud) with data centers located in the United States and the European Union. All data is encrypted at rest using AES-256 encryption and in transit using TLS 1.3.",
      "We implement robust security measures including role-based access controls, regular security audits, penetration testing, automated vulnerability scanning, and multi-factor authentication for internal systems. Our infrastructure is monitored 24/7 for suspicious activity.",
      "Your generated documents and project data are stored in isolated, encrypted database partitions. We perform daily encrypted backups with a 30-day retention period. Backup data is stored in geographically separate locations for disaster recovery.",
      "In the event of a data breach, we will notify affected users within 72 hours in compliance with GDPR requirements, and take immediate action to contain and remediate the incident.",
    ],
  },
  {
    id: "third-party",
    icon: Shield,
    title: "Third-Party Services",
    content: [
      "Velocira integrates with select third-party services to provide core functionality. These include: Stripe for payment processing, Google Cloud AI and OpenAI for document generation, AWS for cloud hosting, and SendGrid for transactional emails.",
      "Each third-party provider is bound by their own privacy policies and our data processing agreements. We carefully vet all providers to ensure they meet our security and privacy standards. We only share the minimum data necessary for each service to function.",
      "We use Google Analytics and Mixpanel for usage analytics, configured to anonymize IP addresses and respect Do Not Track browser signals. You can opt out of analytics tracking through your account settings or by using browser extensions that block tracking scripts.",
      "We do not share, sell, or rent your personal data with third-party advertisers. Any data shared with partners is strictly for the operation and improvement of the Velocira platform.",
    ],
  },
  {
    id: "cookies",
    icon: Cookie,
    title: "Cookies & Tracking Technologies",
    content: [
      "We use essential cookies that are strictly necessary for the operation of our platform, including session management cookies, authentication tokens, and CSRF protection cookies. These cannot be disabled without affecting core functionality.",
      "Performance cookies help us understand how you interact with Velocira by collecting anonymous usage data. These cookies help us identify which features are most popular and where users encounter issues.",
      "We use preference cookies to remember your settings, such as language preference, theme selection (dark/light mode), and dashboard layout configurations. These enhance your experience but are not essential.",
      "You can manage your cookie preferences through your browser settings or our cookie consent banner. Note that disabling certain cookies may limit the functionality of our platform. We honor Do Not Track (DNT) browser signals.",
    ],
  },
  {
    id: "your-rights",
    icon: Users,
    title: "Your Rights (GDPR / CCPA)",
    content: [
      "Under the General Data Protection Regulation (GDPR) and the California Consumer Privacy Act (CCPA), you have the right to access, correct, delete, and port your personal data. You may also object to or restrict certain processing activities.",
      "Right to Access: You can request a complete copy of all personal data we hold about you in a structured, machine-readable format (JSON or CSV). We will fulfill these requests within 30 days.",
      "Right to Deletion: You can request the deletion of your account and all associated data at any time through your account settings or by contacting us. Upon deletion, we will permanently remove your data within 30 days, except where retention is required by law.",
      "Right to Opt Out: You can opt out of AI model training using your data, marketing communications, and analytics tracking at any time. California residents have the additional right to opt out of the sale of personal information, though we do not sell personal data.",
      "To exercise any of these rights, contact us at privacy@velocira.dev or use the data management tools in your account settings. We will not discriminate against you for exercising your privacy rights.",
    ],
  },
  {
    id: "data-retention",
    icon: Clock,
    title: "Data Retention",
    content: [
      "We retain your account information and generated documents for as long as your account is active. If you delete your account, we will remove all personal data within 30 days, though anonymized analytics data may be retained indefinitely.",
      "Transactional records (invoices, payment history) are retained for 7 years to comply with tax and accounting regulations. These records are stored securely and access is restricted to authorized finance personnel.",
      "Server logs containing IP addresses and request data are retained for 90 days for security monitoring and debugging purposes, after which they are automatically purged.",
      "If your account is inactive for more than 24 months, we will send you a notification before archiving your data. Archived data is retained for an additional 6 months before permanent deletion.",
    ],
  },
  {
    id: "children",
    icon: Baby,
    title: "Children's Privacy",
    content: [
      "Velocira is not intended for use by individuals under the age of 16. We do not knowingly collect personal information from children under 16. If you are a parent or guardian and believe your child has provided us with personal data, please contact us immediately.",
      "If we discover that we have inadvertently collected personal information from a child under 16, we will take immediate steps to delete that information from our servers and terminate the associated account.",
      "Educational institutions using Velocira for students aged 16-18 must ensure they have obtained appropriate parental or guardian consent in compliance with applicable laws, including COPPA and local regulations.",
    ],
  },
  {
    id: "changes",
    icon: FileText,
    title: "Changes to This Policy",
    content: [
      "We may update this Privacy Policy from time to time to reflect changes in our practices, technologies, legal requirements, or other factors. When we make material changes, we will notify you via email and/or a prominent notice on our platform at least 30 days before the changes take effect.",
      "We encourage you to review this Privacy Policy periodically to stay informed about how we protect your data. The \"Last Updated\" date at the top of this page indicates when this policy was most recently revised.",
      "Your continued use of Velocira after any changes to this Privacy Policy constitutes your acceptance of the updated terms. If you do not agree with the revised policy, you should discontinue use of the platform and delete your account.",
    ],
  },
  {
    id: "contact",
    icon: Mail,
    title: "Contact Us",
    content: [
      "If you have any questions, concerns, or requests regarding this Privacy Policy or our data practices, please do not hesitate to contact us. We take every inquiry seriously and aim to respond within 48 hours.",
      "Email: privacy@velocira.dev | General Support: support@velocira.dev",
      "You may also reach our Data Protection Officer at dpo@velocira.dev for GDPR-specific inquiries. For EU residents, you have the right to lodge a complaint with your local supervisory authority if you believe your data rights have been violated.",
    ],
  },
];

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function PrivacyPage() {
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
              Your Privacy Matters
            </div>

            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold font-display text-foreground">
              Privacy <span className="gradient-text">Policy</span>
            </h1>

            <p className="mt-6 text-lg text-foreground-secondary max-w-2xl mx-auto leading-relaxed">
              At Velocira, we are committed to protecting your privacy and ensuring the security
              of your personal information. This policy explains how we collect, use, and safeguard your data.
            </p>

            <p className="mt-4 text-sm text-foreground-secondary/60">
              Last updated: February 2026
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

      {/* ───────── Policy Sections ───────── */}
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
              Have Questions About Your <span className="gradient-text">Privacy</span>?
            </h2>
            <p className="mt-4 text-foreground-secondary max-w-xl mx-auto">
              We&apos;re here to help. Reach out to our privacy team and we&apos;ll get back to you within 48 hours.
            </p>
            <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
              <Link href="/contact">
                <Button size="lg" icon={<Mail className="h-5 w-5" />}>
                  Contact Privacy Team
                </Button>
              </Link>
              <Link href="/terms">
                <Button variant="outline" size="lg" icon={<FileText className="h-5 w-5" />}>
                  Terms of Service
                </Button>
              </Link>
            </div>
          </FadeIn>
        </div>
      </section>
    </PageTransition>
  );
}
