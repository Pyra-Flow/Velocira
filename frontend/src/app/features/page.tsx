"use client";

import Link from "next/link";
import { motion } from "framer-motion";
import {
  FileText,
  Users,
  Database,
  Globe,
  Layers,
  CalendarClock,
  Download,
  LayoutDashboard,
  PenLine,
  RefreshCcw,
  ShieldCheck,
  UsersRound,
  Sparkles,
  ArrowRight,
  Check,
  X,
  Zap,
  Brain,
  Code2,
  Server,
  CloudCog,
  GitBranch,
  FileJson,
  Boxes,
  Rocket,
  Clock,
  MousePointerClick,
  Bot,
  ChevronRight,
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
/*  Data                                                               */
/* ------------------------------------------------------------------ */

const showcases = [
  {
    badge: "AI-Powered Generation",
    title: "From Idea to Full SRS in Seconds",
    description:
      "Describe your project in plain language and watch Velocira generate a complete Software Requirements Specification — use cases, ERDs, API structures, and more — all in one click.",
    features: [
      "Natural language project input",
      "Multi-section document generation",
      "Context-aware AI that understands your domain",
      "Supports any project type or tech stack",
    ],
    icon: Brain,
    color: "text-primary",
    bgColor: "bg-primary/10",
    direction: "right" as const,
  },
  {
    badge: "Inline Editing & Regeneration",
    title: "Refine Every Section With Precision",
    description:
      "Edit any generated section with a rich-text editor. Not happy with the result? Provide guidance and regenerate individual sections — without losing the rest of your document.",
    features: [
      "Rich-text editing per section",
      "Targeted section regeneration with prompts",
      "Version history for every change",
      "Markdown & visual editing modes",
    ],
    icon: PenLine,
    color: "text-accent",
    bgColor: "bg-accent/10",
    direction: "left" as const,
  },
  {
    badge: "Export & Collaborate",
    title: "Share, Export, and Ship Documentation",
    description:
      "Export your polished documentation to PDF, DOCX, or Markdown. Manage multiple projects from a single dashboard and (soon) collaborate with your entire team in real-time.",
    features: [
      "PDF, DOCX, and Markdown export",
      "Multi-project dashboard with analytics",
      "Search, sort, and filter projects",
      "Team sharing & permissions (Coming Soon)",
    ],
    icon: Download,
    color: "text-info",
    bgColor: "bg-info/10",
    direction: "right" as const,
  },
];

const allFeatures = [
  {
    icon: FileText,
    title: "SRS Generation",
    description:
      "Generate complete Software Requirements Specifications from a simple project description. Covers functional and non-functional requirements.",
    color: "bg-primary/10 text-primary",
  },
  {
    icon: Users,
    title: "Use Case Generation",
    description:
      "Automatically identify actors, create use cases with detailed flows, and produce UML-ready descriptions for your system.",
    color: "bg-accent/10 text-accent",
  },
  {
    icon: Database,
    title: "ERD Generation",
    description:
      "Extract entities, attributes, and relationships. Get SQL schema drafts and visual-ready ER diagrams for your database layer.",
    color: "bg-info/10 text-info",
  },
  {
    icon: Globe,
    title: "API Structure",
    description:
      "Design RESTful endpoints with request/response examples, status codes, authentication recommendations, and OpenAPI-style docs.",
    color: "bg-success/10 text-success",
  },
  {
    icon: Layers,
    title: "Architecture Proposal",
    description:
      "Receive architecture pattern recommendations, tech stack suggestions, and deployment structure diagrams tailored to your project.",
    color: "bg-warning/10 text-warning",
  },
  {
    icon: CalendarClock,
    title: "Implementation Roadmap",
    description:
      "Get a phased timeline with milestones, sprint breakdowns, and task priorities so your team knows exactly what to build and when.",
    color: "bg-error/10 text-error",
  },
  {
    icon: Download,
    title: "Document Export",
    description:
      "Export your finished documentation in PDF, DOCX, or Markdown. Beautiful formatting that's ready for stakeholders and developers alike.",
    color: "bg-primary/10 text-primary",
  },
  {
    icon: LayoutDashboard,
    title: "Multi-Project Dashboard",
    description:
      "Manage all your projects from one place. Search, sort, view analytics, and jump into any document with a single click.",
    color: "bg-accent/10 text-accent",
  },
  {
    icon: PenLine,
    title: "Inline Editing",
    description:
      "Modify any section of your generated document with a powerful rich-text editor. Full control over every word and diagram.",
    color: "bg-info/10 text-info",
  },
  {
    icon: RefreshCcw,
    title: "Section Regeneration",
    description:
      "Unhappy with a section? Provide new guidance and regenerate just that part — keep everything else intact.",
    color: "bg-success/10 text-success",
  },
  {
    icon: ShieldCheck,
    title: "Admin Panel",
    description:
      "Full user management, platform analytics, and configuration controls for administrators to oversee the entire platform.",
    color: "bg-warning/10 text-warning",
  },
  {
    icon: UsersRound,
    title: "Team Collaboration",
    description:
      "Share projects with teammates, manage roles and permissions, and collaborate on documentation in real-time.",
    tag: "Coming Soon",
    color: "bg-error/10 text-error",
  },
];

const beforeAfter = {
  before: [
    "Spend weeks writing SRS documents manually",
    "Inconsistent formatting across sections",
    "No standardized use case or ERD templates",
    "Copy-pasting API schemas from scratch",
    "Scattered documents in Google Docs & Notion",
    "No version history or section-level control",
  ],
  after: [
    "Generate a complete SRS in under a minute",
    "Consistent, professional formatting every time",
    "Auto-generated use cases, ERDs, and UML",
    "Full API documentation with examples",
    "Centralized dashboard for all projects",
    "Inline editing & per-section regeneration",
  ],
};

const integrations = [
  { name: "React", icon: Code2 },
  { name: "Node.js", icon: Server },
  { name: "Python", icon: FileJson },
  { name: "Spring Boot", icon: Boxes },
  { name: "AWS / GCP", icon: CloudCog },
  { name: "Docker", icon: Layers },
  { name: "REST APIs", icon: Globe },
  { name: "Git", icon: GitBranch },
];

/* ------------------------------------------------------------------ */
/*  Mock UI Preview Component                                          */
/* ------------------------------------------------------------------ */

function MockUIPreview({
  variant,
}: {
  variant: "generation" | "editing" | "dashboard";
}) {
  const lines =
    variant === "generation"
      ? [
          { w: "w-3/4", label: "1. Introduction", accent: true },
          { w: "w-full", label: "Project overview and scope definition..." },
          { w: "w-5/6", label: "2. Functional Requirements", accent: true },
          { w: "w-full", label: "FR-001: User registration and authentication" },
          { w: "w-4/5", label: "FR-002: AI-powered document generation" },
          { w: "w-full", label: "3. Use Case Diagram", accent: true },
          { w: "w-3/4", label: "Actor: End User → Generate SRS" },
        ]
      : variant === "editing"
        ? [
            { w: "w-2/3", label: "Section: API Structure", accent: true },
            { w: "w-full", label: "POST /api/v1/projects" },
            { w: "w-5/6", label: '  → 201 Created { "id": "proj_abc" }' },
            { w: "w-full", label: "GET  /api/v1/projects/:id/docs" },
            { w: "w-4/5", label: "  → 200 OK { sections: [...] }" },
            { w: "w-3/4", label: "Authentication: Bearer JWT", accent: true },
          ]
        : [
            { w: "w-full", label: "📁 E-Commerce Platform — 12 sections", accent: true },
            { w: "w-5/6", label: "📁 Healthcare App — 8 sections" },
            { w: "w-4/5", label: "📁 FinTech Dashboard — 15 sections" },
            { w: "w-full", label: "Total documents: 35 | Last active: 2 min ago", accent: true },
          ];

  return (
    <motion.div
      className="relative rounded-2xl border border-border bg-card overflow-hidden shadow-lg"
      initial={{ opacity: 0, scale: 0.95 }}
      whileInView={{ opacity: 1, scale: 1 }}
      viewport={{ once: true }}
      transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
    >
      {/* Title bar */}
      <div className="flex items-center gap-2 px-4 py-3 border-b border-border bg-background-secondary/50">
        <span className="h-3 w-3 rounded-full bg-error/60" />
        <span className="h-3 w-3 rounded-full bg-warning/60" />
        <span className="h-3 w-3 rounded-full bg-success/60" />
        <span className="ms-3 text-xs text-foreground-secondary font-mono">
          {variant === "generation"
            ? "velocira — srs-generation"
            : variant === "editing"
              ? "velocira — inline-editor"
              : "velocira — dashboard"}
        </span>
      </div>

      {/* Content */}
      <div className="p-5 space-y-3">
        {lines.map((line, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, x: -12 }}
            whileInView={{ opacity: 1, x: 0 }}
            viewport={{ once: true }}
            transition={{ delay: 0.15 + i * 0.08 }}
            className={`${line.w} flex items-center gap-2`}
          >
            {line.accent && (
              <span className="h-2 w-2 rounded-full bg-primary shrink-0" />
            )}
            <span
              className={`text-sm font-mono truncate ${
                line.accent
                  ? "text-primary font-semibold"
                  : "text-foreground-secondary"
              }`}
            >
              {line.label}
            </span>
          </motion.div>
        ))}

        {/* Blinking cursor */}
        <motion.span
          animate={{ opacity: [1, 0] }}
          transition={{ duration: 0.8, repeat: Infinity }}
          className="inline-block h-4 w-1.5 bg-primary rounded-sm"
        />
      </div>
    </motion.div>
  );
}

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function FeaturesPage() {
  const { t } = useLocale();
  void t; // translations available for future i18n

  return (
    <PageTransition>
      {/* ───────── Hero ───────── */}
      <section className="relative overflow-hidden py-24 sm:py-32">
        
        
        {/* Geometric accents */}
        <div className="absolute top-16 right-[8%] w-32 h-32 border border-primary/[0.06] rotate-45 pointer-events-none hidden lg:block" />
        <div className="absolute bottom-16 left-[6%] w-20 h-20 border border-primary/[0.05] rotate-45 pointer-events-none hidden lg:block" />

        <div className="relative max-w-4xl mx-auto px-4 text-center">
          <FadeIn>
            <motion.div
              initial={{ scale: 0.9 }}
              animate={{ scale: 1 }}
              className="inline-flex items-center gap-2 px-5 py-2 rounded-full border border-primary/20 bg-primary/[0.06] text-primary text-sm font-medium mb-8"
            >
              <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
              Platform Features
            </motion.div>

            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold font-display leading-tight">
              Everything You Need to{" "}
              <span className="gradient-text">Build Better Docs</span>
            </h1>

            <p className="mt-6 text-lg sm:text-xl text-foreground-secondary max-w-2xl mx-auto leading-relaxed">
              Velocira combines the power of AI with an intuitive editing
              experience to turn your project ideas into complete, professional
              documentation — in seconds, not weeks.
            </p>

            <div className="mt-10 flex flex-wrap items-center justify-center gap-4">
              <Link href="/register">
                <Button size="lg" icon={<Rocket className="h-5 w-5" />}>
                  Get Started Free
                </Button>
              </Link>
              <Link href="#features-grid">
                <Button
                  variant="outline"
                  size="lg"
                  icon={<MousePointerClick className="h-5 w-5" />}
                >
                  Explore Features
                </Button>
              </Link>
            </div>
          </FadeIn>
        </div>
      </section>

      {/* ───────── Showcase Sections (alternating) ───────── */}
      {showcases.map((item, idx) => {
        const variants = ["generation", "editing", "dashboard"] as const;
        const isEven = idx % 2 === 0;

        return (
          <section
            key={item.title}
            className={`py-20 ${idx % 2 === 1 ? "bg-card/30" : ""}`}
          >
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
              <div
                className="grid lg:grid-cols-2 gap-12 lg:gap-16 items-center"
              >
                {/* Text */}
                <FadeIn
                  direction={item.direction}
                  className={isEven ? "lg:order-1" : "lg:order-2"}
                >
                  <div className="space-y-6">
                    <div className="inline-flex items-center gap-2 text-sm font-medium">
                      <div
                        className={`h-8 w-8 rounded-lg ${item.bgColor} flex items-center justify-center`}
                      >
                        <item.icon className={`h-4 w-4 ${item.color}`} />
                      </div>
                      <span className={item.color}>{item.badge}</span>
                    </div>

                    <h2 className="text-3xl sm:text-4xl font-bold font-display text-foreground">
                      {item.title}
                    </h2>

                    <p className="text-foreground-secondary text-lg leading-relaxed">
                      {item.description}
                    </p>

                    <ul className="space-y-3 pt-2">
                      {item.features.map((feat) => (
                        <li key={feat} className="flex items-start gap-3">
                          <span className="mt-1 h-5 w-5 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                            <Check className="h-3 w-3 text-primary" />
                          </span>
                          <span className="text-foreground-secondary">
                            {feat}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </FadeIn>

                {/* Mock UI */}
                <FadeIn
                  direction={item.direction === "right" ? "left" : "right"}
                  className={isEven ? "lg:order-2" : "lg:order-1"}
                >
                  <MockUIPreview variant={variants[idx]} />
                </FadeIn>
              </div>
            </div>
          </section>
        );
      })}

      {/* ───────── Full Feature Grid ───────── */}
      <section id="features-grid" className="py-24 bg-card/30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-4xl font-bold font-display text-foreground">
                Powerful Features,{" "}
                <span className="gradient-text">Zero Complexity</span>
              </h2>
              <p className="mt-4 text-foreground-secondary text-lg max-w-2xl mx-auto">
                Every tool you need to go from an idea to production-ready
                documentation — powered by AI and designed for developers.
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="grid sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
            {allFeatures.map((feat) => (
              <StaggerItem key={feat.title}>
                <Card hover className="h-full relative group">
                  <div
                    className={`h-11 w-11 rounded-xl ${feat.color} flex items-center justify-center mb-4 transition-transform group-hover:scale-110`}
                  >
                    <feat.icon className="h-5 w-5" />
                  </div>

                  <h3 className="text-base font-semibold text-foreground mb-2 font-display flex items-center gap-2">
                    {feat.title}
                    {feat.tag && (
                      <span className="text-[10px] uppercase tracking-wider px-2 py-0.5 rounded-full bg-warning/10 text-warning font-medium">
                        {feat.tag}
                      </span>
                    )}
                  </h3>

                  <p className="text-sm text-foreground-secondary leading-relaxed">
                    {feat.description}
                  </p>
                </Card>
              </StaggerItem>
            ))}
          </StaggerContainer>
        </div>
      </section>

      {/* ───────── Before / After Comparison ───────── */}
      <section className="py-24">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-4xl font-bold font-display text-foreground">
                The <span className="gradient-text">Velocira</span> Difference
              </h2>
              <p className="mt-4 text-foreground-secondary text-lg max-w-2xl mx-auto">
                See how Velocira transforms the way teams create and manage
                technical documentation.
              </p>
            </div>
          </FadeIn>

          <div className="grid md:grid-cols-2 gap-8">
            {/* Before */}
            <FadeIn direction="right">
              <Card className="h-full border-error/20 bg-error/[0.03]">
                <div className="flex items-center gap-3 mb-6">
                  <div className="h-10 w-10 rounded-xl bg-error/10 flex items-center justify-center">
                    <Clock className="h-5 w-5 text-error" />
                  </div>
                  <h3 className="text-xl font-bold font-display text-foreground">
                    Before Velocira
                  </h3>
                </div>
                <ul className="space-y-4">
                  {beforeAfter.before.map((item) => (
                    <li key={item} className="flex items-start gap-3">
                      <span className="mt-0.5 h-5 w-5 rounded-full bg-error/10 flex items-center justify-center shrink-0">
                        <X className="h-3 w-3 text-error" />
                      </span>
                      <span className="text-foreground-secondary">{item}</span>
                    </li>
                  ))}
                </ul>
              </Card>
            </FadeIn>

            {/* After */}
            <FadeIn direction="left">
              <Card className="h-full border-success/20 bg-success/[0.03]">
                <div className="flex items-center gap-3 mb-6">
                  <div className="h-10 w-10 rounded-xl bg-success/10 flex items-center justify-center">
                    <Zap className="h-5 w-5 text-success" />
                  </div>
                  <h3 className="text-xl font-bold font-display text-foreground">
                    After Velocira
                  </h3>
                </div>
                <ul className="space-y-4">
                  {beforeAfter.after.map((item) => (
                    <li key={item} className="flex items-start gap-3">
                      <span className="mt-0.5 h-5 w-5 rounded-full bg-success/10 flex items-center justify-center shrink-0">
                        <Check className="h-3 w-3 text-success" />
                      </span>
                      <span className="text-foreground-secondary">{item}</span>
                    </li>
                  ))}
                </ul>
              </Card>
            </FadeIn>
          </div>
        </div>
      </section>

      {/* ───────── Integrations / Tech Stack ───────── */}
      <section className="py-24 bg-card/30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-4xl font-bold font-display text-foreground">
                Works With Your{" "}
                <span className="gradient-text">Tech Stack</span>
              </h2>
              <p className="mt-4 text-foreground-secondary text-lg max-w-2xl mx-auto">
                No matter what you&apos;re building — Velocira understands your
                stack and generates documentation tailored to it.
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="grid grid-cols-2 sm:grid-cols-4 gap-5">
            {integrations.map((tech) => (
              <StaggerItem key={tech.name}>
                <Card
                  hover
                  className="flex flex-col items-center justify-center py-8 text-center"
                >
                  <div className="h-12 w-12 rounded-xl bg-primary/10 flex items-center justify-center mb-3">
                    <tech.icon className="h-6 w-6 text-primary" />
                  </div>
                  <span className="text-sm font-medium text-foreground">
                    {tech.name}
                  </span>
                </Card>
              </StaggerItem>
            ))}
          </StaggerContainer>
        </div>
      </section>

      {/* ───────── Bottom CTA ───────── */}
      <section className="relative overflow-hidden py-24 sm:py-32">
        

        <div className="relative max-w-3xl mx-auto px-4 text-center">
          <FadeIn>
            <div className="inline-flex items-center gap-2 px-5 py-2 rounded-full border border-primary/20 bg-primary/[0.06] text-primary text-sm font-medium mb-8">
              <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
              AI-Powered Documentation
            </div>

            <h2 className="text-3xl sm:text-4xl lg:text-5xl font-bold font-display text-foreground">
              Ready to{" "}
              <span className="gradient-text">Supercharge</span> Your
              Documentation?
            </h2>

            <p className="mt-6 text-lg text-foreground-secondary max-w-xl mx-auto leading-relaxed">
              Join developers and teams who are already shipping
              professional-grade documentation 10× faster with Velocira.
            </p>

            <div className="mt-10 flex flex-wrap items-center justify-center gap-4">
              <Link href="/register">
                <Button size="lg" icon={<Rocket className="h-5 w-5" />}>
                  Start Building — It&apos;s Free
                </Button>
              </Link>
              <Link href="/contact">
                <Button
                  variant="ghost"
                  size="lg"
                  icon={<ChevronRight className="h-5 w-5" />}
                >
                  Talk to Us
                </Button>
              </Link>
            </div>

            <p className="mt-6 text-sm text-foreground-secondary/60">
              No credit card required · Free tier available · Setup in under 60
              seconds
            </p>
          </FadeIn>
        </div>
      </section>
    </PageTransition>
  );
}
