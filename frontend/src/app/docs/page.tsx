"use client";

import { useState } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import {
  Search,
  BookOpen,
  Rocket,
  FolderKanban,
  FileText,
  Database,
  Code2,
  Share2,
  ChevronRight,
  ChevronDown,
  Play,
  ArrowRight,
  Zap,
  Terminal,
  Copy,
  Check,
  ExternalLink,
  HelpCircle,
  Lightbulb,
  UserPlus,
  Layers,
  Sparkles,
  Globe,
  Lock,
  Clock,
  CreditCard,
  Settings,
  Download,
  Video,
  CirclePlay,
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

const gettingStartedSteps = [
  {
    step: 1,
    title: "Create Your Account",
    description:
      "Sign up for free with email or OAuth. No credit card required to get started.",
    icon: UserPlus,
    color: "text-primary",
    bgColor: "bg-primary/10",
  },
  {
    step: 2,
    title: "Create a Project",
    description:
      "Name your project, set the domain, and choose your preferred language.",
    icon: FolderKanban,
    color: "text-accent",
    bgColor: "bg-accent/10",
  },
  {
    step: 3,
    title: "Enter Your Idea",
    description:
      "Describe your software concept in plain language — a sentence or a paragraph is enough.",
    icon: Lightbulb,
    color: "text-warning",
    bgColor: "bg-warning/10",
  },
  {
    step: 4,
    title: "Select Document Type",
    description:
      "Choose from SRS, ERD, API docs, architecture overview, or generate them all.",
    icon: Layers,
    color: "text-info",
    bgColor: "bg-info/10",
  },
  {
    step: 5,
    title: "Generate with AI",
    description:
      "Hit generate and watch Velocira craft production-grade documentation in seconds.",
    icon: Sparkles,
    color: "text-primary",
    bgColor: "bg-primary/10",
  },
  {
    step: 6,
    title: "Review & Edit",
    description:
      "Fine-tune every section with our rich editor. Regenerate individual parts with custom prompts.",
    icon: FileText,
    color: "text-success",
    bgColor: "bg-success/10",
  },
  {
    step: 7,
    title: "Export & Share",
    description:
      "Export as PDF, Markdown, or HTML. Share live links with your team or stakeholders.",
    icon: Share2,
    color: "text-accent",
    bgColor: "bg-accent/10",
  },
];

const docCategories = [
  {
    title: "Quick Start Guide",
    description:
      "Get up and running in under 5 minutes. Create your first AI-generated document.",
    icon: Rocket,
    href: "/docs/quick-start",
    badge: "Popular",
    badgeColor: "bg-primary/10 text-primary",
    articles: 8,
    readTime: "5 min",
  },
  {
    title: "Project Setup",
    description:
      "Learn how to configure projects, set team permissions, and manage workspaces.",
    icon: FolderKanban,
    href: "/docs/project-setup",
    badge: null,
    badgeColor: "",
    articles: 12,
    readTime: "10 min",
  },
  {
    title: "SRS Generation",
    description:
      "Deep dive into AI-powered Software Requirements Specifications — prompts, sections, and best practices.",
    icon: FileText,
    href: "/docs/srs-generation",
    badge: "Core",
    badgeColor: "bg-accent/10 text-accent",
    articles: 15,
    readTime: "15 min",
  },
  {
    title: "ERD & Data Models",
    description:
      "Auto-generate entity relationship diagrams and data models from your project description.",
    icon: Database,
    href: "/docs/erd-data-models",
    badge: null,
    badgeColor: "",
    articles: 9,
    readTime: "12 min",
  },
  {
    title: "API Documentation",
    description:
      "Generate RESTful API specs, endpoint tables, request/response schemas, and OpenAPI exports.",
    icon: Code2,
    href: "/docs/api-documentation",
    badge: "New",
    badgeColor: "bg-success/10 text-success",
    articles: 11,
    readTime: "10 min",
  },
  {
    title: "Export & Sharing",
    description:
      "Export to PDF, Markdown, HTML, or Confluence. Share live docs with public or private links.",
    icon: Share2,
    href: "/docs/export-sharing",
    badge: null,
    badgeColor: "",
    articles: 7,
    readTime: "8 min",
  },
];

const apiEndpoints = [
  {
    method: "GET",
    endpoint: "/api/v1/projects",
    description: "List all projects for the authenticated user",
    auth: true,
  },
  {
    method: "POST",
    endpoint: "/api/v1/projects",
    description: "Create a new project with AI generation config",
    auth: true,
  },
  {
    method: "GET",
    endpoint: "/api/v1/projects/:id/documents",
    description: "Retrieve all generated documents for a project",
    auth: true,
  },
  {
    method: "POST",
    endpoint: "/api/v1/generate",
    description: "Trigger AI document generation for a project",
    auth: true,
  },
  {
    method: "PATCH",
    endpoint: "/api/v1/documents/:id/sections/:sectionId",
    description: "Update or regenerate a specific document section",
    auth: true,
  },
  {
    method: "POST",
    endpoint: "/api/v1/documents/:id/export",
    description: "Export a document to PDF, Markdown, or HTML",
    auth: true,
  },
  {
    method: "GET",
    endpoint: "/api/v1/user/profile",
    description: "Get current user profile and subscription info",
    auth: true,
  },
  {
    method: "POST",
    endpoint: "/api/v1/auth/token",
    description: "Generate an API access token",
    auth: false,
  },
];

const faqItems = [
  {
    question: "What is Velocira and how does it work?",
    answer:
      "Velocira is an AI-powered documentation platform that transforms your software ideas into production-grade technical documents. Simply describe your project in plain language, select the document types you need (SRS, ERD, API docs, etc.), and our AI generates comprehensive, structured documentation in seconds.",
  },
  {
    question: "Do I need technical expertise to use Velocira?",
    answer:
      "Not at all. Velocira is designed for everyone — from product managers to senior engineers. You describe your idea in everyday language, and our AI handles the technical formatting, structure, and best practices. You can then refine the output with our intuitive editor.",
  },
  {
    question: "What types of documents can Velocira generate?",
    answer:
      "Velocira generates Software Requirements Specifications (SRS), Entity Relationship Diagrams (ERD), API documentation, architecture overviews, user flow diagrams, data dictionaries, and more. We continuously add new document types based on user feedback.",
  },
  {
    question: "Can I edit the generated documents?",
    answer:
      "Absolutely. Every section of a generated document is fully editable with our rich-text editor. You can also provide custom prompts to regenerate individual sections without affecting the rest of your document. Version history tracks every change.",
  },
  {
    question: "What export formats are supported?",
    answer:
      "You can export your documents as PDF, Markdown, HTML, or Confluence-compatible format. We also support live shareable links with optional password protection and expiration dates.",
  },
  {
    question: "Is there an API I can integrate with?",
    answer:
      "Yes! Velocira provides a full REST API that lets you programmatically create projects, trigger generation, update sections, and export documents. API keys can be generated from your dashboard settings.",
  },
  {
    question: "How secure is my data?",
    answer:
      "Security is a top priority. All data is encrypted at rest (AES-256) and in transit (TLS 1.3). We are SOC 2 Type II compliant, and you can configure data retention policies per project. Enterprise plans include SSO and dedicated infrastructure.",
  },
  {
    question: "What AI model powers the generation?",
    answer:
      "Velocira uses a proprietary multi-model pipeline that combines large language models fine-tuned on millions of technical documents. The system is optimized for accuracy, consistency, and adherence to industry documentation standards.",
  },
  {
    question: "Is there a free plan available?",
    answer:
      "Yes! Our free tier includes up to 3 projects, 10 document generations per month, and PDF export. Paid plans unlock unlimited projects, priority generation, team collaboration, custom branding, and API access.",
  },
  {
    question: "Can I collaborate with my team?",
    answer:
      "Team collaboration is available on Pro and Enterprise plans. You can invite team members, assign roles (viewer, editor, admin), leave comments on sections, and track changes with a full audit log.",
  },
];

const videoTutorials = [
  {
    title: "Getting Started with Velocira",
    duration: "4:32",
    thumbnail: "Create your first project and generate an SRS in minutes.",
    category: "Beginner",
  },
  {
    title: "Advanced Prompt Engineering",
    duration: "8:15",
    thumbnail:
      "Learn how to craft precise prompts for better document generation results.",
    category: "Advanced",
  },
  {
    title: "API Integration Tutorial",
    duration: "12:47",
    thumbnail:
      "Integrate Velocira into your CI/CD pipeline with our REST API.",
    category: "Developer",
  },
];

const codeExample = `// Initialize the Velocira SDK
import { Velocira } from '@velocira/sdk';

const client = new Velocira({
  apiKey: process.env.VELOCIRA_API_KEY,
});

// Create a new project
const project = await client.projects.create({
  name: 'E-Commerce Platform',
  description: 'A modern e-commerce platform with AI recommendations',
  language: 'en',
});

// Generate full documentation
const docs = await client.generate({
  projectId: project.id,
  types: ['srs', 'erd', 'api-docs'],
  options: {
    detailLevel: 'comprehensive',
    includeExamples: true,
  },
});

// Export as PDF
const pdf = await client.documents.export({
  documentId: docs.srs.id,
  format: 'pdf',
});

console.log('Documentation generated!', pdf.downloadUrl);`;

const curlExample = `# Authenticate and get an API token
curl -X POST https://api.velocira.dev/v1/auth/token \\
  -H "Content-Type: application/json" \\
  -d '{"email": "you@example.com", "password": "••••••••"}'

# Create a project
curl -X POST https://api.velocira.dev/v1/projects \\
  -H "Authorization: Bearer YOUR_TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '{
    "name": "My SaaS App",
    "description": "A project management tool with AI features"
  }'

# Trigger document generation
curl -X POST https://api.velocira.dev/v1/generate \\
  -H "Authorization: Bearer YOUR_TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '{
    "projectId": "proj_abc123",
    "types": ["srs", "erd"]
  }'`;

/* ------------------------------------------------------------------ */
/*  Sub-components                                                     */
/* ------------------------------------------------------------------ */

function MethodBadge({ method }: { method: string }) {
  const colors: Record<string, string> = {
    GET: "bg-success/15 text-success",
    POST: "bg-primary/15 text-primary",
    PATCH: "bg-warning/15 text-warning",
    PUT: "bg-info/15 text-info",
    DELETE: "bg-error/15 text-error",
  };

  return (
    <span
      className={`inline-flex items-center rounded-lg px-2.5 py-1 text-xs font-bold font-mono tracking-wide ${colors[method] ?? "bg-card text-foreground-secondary"}`}
    >
      {method}
    </span>
  );
}

function FAQItem({
  question,
  answer,
  isOpen,
  onToggle,
}: {
  question: string;
  answer: string;
  isOpen: boolean;
  onToggle: () => void;
}) {
  return (
    <motion.div
      className="border border-border rounded-2xl overflow-hidden transition-colors hover:border-border-hover"
      layout
    >
      <button
        onClick={onToggle}
        className="flex w-full items-center justify-between p-5 text-left cursor-pointer"
      >
        <span className="text-foreground font-medium pr-4">{question}</span>
        <motion.span
          animate={{ rotate: isOpen ? 180 : 0 }}
          transition={{ duration: 0.2 }}
          className="shrink-0 text-foreground-secondary"
        >
          <ChevronDown className="h-5 w-5" />
        </motion.span>
      </button>
      <AnimatePresence initial={false}>
        {isOpen && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
          >
            <div className="px-5 pb-5 text-foreground-secondary leading-relaxed">
              {answer}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}

function CodeBlock({
  code,
  language,
  filename,
}: {
  code: string;
  language: string;
  filename: string;
}) {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="rounded-2xl border border-border overflow-hidden">
      {/* Header bar */}
      <div className="flex items-center justify-between bg-background-secondary px-4 py-3 border-b border-border">
        <div className="flex items-center gap-3">
          <div className="flex gap-1.5">
            <span className="h-3 w-3 rounded-full bg-error/60" />
            <span className="h-3 w-3 rounded-full bg-warning/60" />
            <span className="h-3 w-3 rounded-full bg-success/60" />
          </div>
          <span className="text-xs text-foreground-secondary font-mono">
            {filename}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs text-foreground-secondary/60 uppercase tracking-wider">
            {language}
          </span>
          <button
            onClick={handleCopy}
            className="p-1.5 rounded-lg hover:bg-card transition-colors text-foreground-secondary hover:text-foreground cursor-pointer"
          >
            {copied ? (
              <Check className="h-4 w-4 text-success" />
            ) : (
              <Copy className="h-4 w-4" />
            )}
          </button>
        </div>
      </div>
      {/* Code content */}
      <div className="bg-base-darkest p-5 overflow-x-auto">
        <pre className="text-sm font-mono leading-relaxed text-foreground-secondary whitespace-pre">
          {code}
        </pre>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function DocsPage() {
  const { t } = useLocale();
  const [searchQuery, setSearchQuery] = useState("");
  const [openFAQ, setOpenFAQ] = useState<number | null>(null);
  const [activeCodeTab, setActiveCodeTab] = useState<"sdk" | "curl">("sdk");

  return (
    <PageTransition>
      <div className="min-h-screen">
        {/* ── Hero ─────────────────────────────────────────────── */}
        <section className="relative overflow-hidden border-b border-border">
          {/* Background glow */}
          <div className="pointer-events-none absolute inset-0">
            
          </div>

          <div className="relative mx-auto max-w-7xl px-6 pb-16 pt-32 sm:pt-40">
            <FadeIn>
              <div className="flex flex-col items-center text-center">
                <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-border bg-card px-4 py-2 text-sm text-foreground-secondary">
                  <BookOpen className="h-4 w-4 text-primary" />
                  Documentation & Help Center
                </div>

                <h1 className="font-display text-4xl font-bold tracking-tight text-foreground sm:text-5xl lg:text-6xl max-w-4xl">
                  Everything you need to{" "}
                  <span className="gradient-text">build faster</span>
                </h1>

                <p className="mt-6 max-w-2xl text-lg text-foreground-secondary leading-relaxed">
                  Comprehensive guides, API references, and tutorials to help
                  you get the most out of Velocira&apos;s AI-powered
                  documentation platform.
                </p>

                {/* Search bar */}
                <div className="mt-10 w-full max-w-2xl">
                  <div className="relative">
                    <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-foreground-secondary" />
                    <input
                      type="text"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      placeholder="Search documentation..."
                      className="w-full rounded-2xl border border-border bg-card py-4 pl-12 pr-4 text-foreground placeholder:text-foreground-secondary/50 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all"
                    />
                    <div className="absolute right-3 top-1/2 -translate-y-1/2">
                      <kbd className="hidden sm:inline-flex items-center gap-1 rounded-lg border border-border bg-background-secondary px-2 py-1 text-xs text-foreground-secondary">
                        ⌘K
                      </kbd>
                    </div>
                  </div>
                </div>

                {/* Quick links */}
                <div className="mt-6 flex flex-wrap justify-center gap-3">
                  {["Quick Start", "API Reference", "SRS Guide", "FAQ"].map(
                    (label) => (
                      <span
                        key={label}
                        className="rounded-full border border-border bg-card px-3 py-1.5 text-sm text-foreground-secondary hover:border-border-hover hover:text-foreground transition-colors cursor-pointer"
                      >
                        {label}
                      </span>
                    )
                  )}
                </div>
              </div>
            </FadeIn>
          </div>
        </section>

        {/* ── Getting Started ──────────────────────────────────── */}
        <section className="mx-auto max-w-7xl px-6 py-24">
          <FadeIn>
            <div className="text-center mb-16">
              <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-primary/10 px-4 py-2 text-sm font-medium text-primary">
                <Rocket className="h-4 w-4" />
                Getting Started
              </div>
              <h2 className="font-display text-3xl font-bold text-foreground sm:text-4xl">
                From zero to documentation in{" "}
                <span className="gradient-text">7 simple steps</span>
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                Follow this step-by-step guide to create your first
                AI-generated document. The entire process takes less than 5
                minutes.
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="relative">
            {/* Vertical connector line */}
            <div className="absolute left-8 top-0 bottom-0 w-px bg-border hidden lg:block" />

            <div className="space-y-6">
              {gettingStartedSteps.map((step, idx) => (
                <StaggerItem key={step.step}>
                  <div className="relative flex items-start gap-6 lg:gap-8">
                    {/* Step number circle */}
                    <div className="relative z-10 hidden lg:flex">
                      <div
                        className={`flex h-16 w-16 items-center justify-center rounded-2xl ${step.bgColor} border border-border`}
                      >
                        <step.icon className={`h-7 w-7 ${step.color}`} />
                      </div>
                    </div>

                    {/* Content card */}
                    <Card hover className="flex-1 group">
                      <div className="flex items-start gap-4">
                        <div
                          className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${step.bgColor} lg:hidden`}
                        >
                          <step.icon className={`h-5 w-5 ${step.color}`} />
                        </div>
                        <div className="flex-1">
                          <div className="flex items-center gap-3 mb-1">
                            <span className="text-xs font-bold text-foreground-secondary/50 uppercase tracking-widest">
                              Step {step.step}
                            </span>
                          </div>
                          <h3 className="text-lg font-semibold text-foreground">
                            {step.title}
                          </h3>
                          <p className="mt-1 text-foreground-secondary leading-relaxed">
                            {step.description}
                          </p>
                        </div>
                        <ChevronRight className="h-5 w-5 shrink-0 text-foreground-secondary/30 group-hover:text-primary transition-colors hidden sm:block" />
                      </div>
                    </Card>
                  </div>
                </StaggerItem>
              ))}
            </div>
          </StaggerContainer>

          <FadeIn delay={0.3}>
            <div className="mt-12 text-center">
              <Link href="/register">
                <Button
                  size="lg"
                  icon={<ArrowRight className="h-5 w-5" />}
                >
                  Start Building for Free
                </Button>
              </Link>
            </div>
          </FadeIn>
        </section>

        {/* ── Documentation Categories ─────────────────────────── */}
        <section className="border-t border-border bg-background-secondary">
          <div className="mx-auto max-w-7xl px-6 py-24">
            <FadeIn>
              <div className="text-center mb-16">
                <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-primary/10 px-4 py-2 text-sm font-medium text-primary">
                  <BookOpen className="h-4 w-4" />
                  Browse by Category
                </div>
                <h2 className="font-display text-3xl font-bold text-foreground sm:text-4xl">
                  Documentation{" "}
                  <span className="gradient-text">categories</span>
                </h2>
                <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                  Explore our comprehensive library organized by topic. Each
                  category contains step-by-step guides, best practices, and
                  real-world examples.
                </p>
              </div>
            </FadeIn>

            <StaggerContainer className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {docCategories.map((cat) => (
                <StaggerItem key={cat.title}>
                  <Link href={cat.href} className="group block h-full">
                    <Card hover glow className="h-full relative overflow-hidden">
                      {/* Badge */}
                      {cat.badge && (
                        <span
                          className={`absolute top-4 right-4 rounded-full px-2.5 py-1 text-xs font-semibold ${cat.badgeColor}`}
                        >
                          {cat.badge}
                        </span>
                      )}

                      <div className="flex flex-col h-full">
                        <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10">
                          <cat.icon className="h-6 w-6 text-primary" />
                        </div>

                        <h3 className="text-lg font-semibold text-foreground group-hover:text-primary transition-colors">
                          {cat.title}
                        </h3>
                        <p className="mt-2 flex-1 text-sm text-foreground-secondary leading-relaxed">
                          {cat.description}
                        </p>

                        <div className="mt-5 flex items-center justify-between border-t border-border pt-4">
                          <div className="flex items-center gap-4 text-xs text-foreground-secondary">
                            <span className="flex items-center gap-1">
                              <FileText className="h-3.5 w-3.5" />
                              {cat.articles} articles
                            </span>
                            <span className="flex items-center gap-1">
                              <Clock className="h-3.5 w-3.5" />
                              {cat.readTime}
                            </span>
                          </div>
                          <ArrowRight className="h-4 w-4 text-foreground-secondary/30 group-hover:text-primary group-hover:translate-x-1 transition-all" />
                        </div>
                      </div>
                    </Card>
                  </Link>
                </StaggerItem>
              ))}
            </StaggerContainer>
          </div>
        </section>

        {/* ── API Reference ────────────────────────────────────── */}
        <section className="mx-auto max-w-7xl px-6 py-24">
          <FadeIn>
            <div className="text-center mb-16">
              <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-primary/10 px-4 py-2 text-sm font-medium text-primary">
                <Terminal className="h-4 w-4" />
                API Reference
              </div>
              <h2 className="font-display text-3xl font-bold text-foreground sm:text-4xl">
                Integrate with the{" "}
                <span className="gradient-text">Velocira API</span>
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                Programmatically create projects, generate documentation, and
                export files with our RESTful API. Full OpenAPI spec available.
              </p>
            </div>
          </FadeIn>

          {/* Endpoints table */}
          <FadeIn delay={0.1}>
            <Card className="overflow-hidden mb-12">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border">
                      <th className="px-5 py-4 text-left text-xs font-semibold text-foreground-secondary uppercase tracking-wider">
                        Method
                      </th>
                      <th className="px-5 py-4 text-left text-xs font-semibold text-foreground-secondary uppercase tracking-wider">
                        Endpoint
                      </th>
                      <th className="px-5 py-4 text-left text-xs font-semibold text-foreground-secondary uppercase tracking-wider hidden md:table-cell">
                        Description
                      </th>
                      <th className="px-5 py-4 text-left text-xs font-semibold text-foreground-secondary uppercase tracking-wider">
                        Auth
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {apiEndpoints.map((ep, idx) => (
                      <tr
                        key={idx}
                        className="hover:bg-card-hover transition-colors group"
                      >
                        <td className="px-5 py-3.5">
                          <MethodBadge method={ep.method} />
                        </td>
                        <td className="px-5 py-3.5">
                          <code className="text-sm font-mono text-foreground group-hover:text-primary transition-colors">
                            {ep.endpoint}
                          </code>
                        </td>
                        <td className="px-5 py-3.5 text-foreground-secondary hidden md:table-cell">
                          {ep.description}
                        </td>
                        <td className="px-5 py-3.5">
                          {ep.auth ? (
                            <span className="inline-flex items-center gap-1 text-xs text-warning">
                              <Lock className="h-3.5 w-3.5" />
                              Bearer
                            </span>
                          ) : (
                            <span className="text-xs text-foreground-secondary">
                              Public
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          </FadeIn>

          {/* Code examples */}
          <FadeIn delay={0.2}>
            <div className="mb-6 flex items-center gap-2">
              <h3 className="text-xl font-semibold text-foreground">
                Code Examples
              </h3>
              <Zap className="h-5 w-5 text-primary" />
            </div>

            {/* Tab switcher */}
            <div className="mb-4 flex gap-2">
              {[
                { key: "sdk" as const, label: "JavaScript SDK", icon: Code2 },
                { key: "curl" as const, label: "cURL", icon: Terminal },
              ].map((tab) => (
                <button
                  key={tab.key}
                  onClick={() => setActiveCodeTab(tab.key)}
                  className={`inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium transition-all cursor-pointer ${
                    activeCodeTab === tab.key
                      ? "bg-primary/10 text-primary border border-primary/30"
                      : "bg-card text-foreground-secondary border border-border hover:border-border-hover hover:text-foreground"
                  }`}
                >
                  <tab.icon className="h-4 w-4" />
                  {tab.label}
                </button>
              ))}
            </div>

            <AnimatePresence mode="wait">
              <motion.div
                key={activeCodeTab}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                transition={{ duration: 0.2 }}
              >
                {activeCodeTab === "sdk" ? (
                  <CodeBlock
                    code={codeExample}
                    language="JavaScript"
                    filename="generate-docs.js"
                  />
                ) : (
                  <CodeBlock
                    code={curlExample}
                    language="Bash"
                    filename="terminal"
                  />
                )}
              </motion.div>
            </AnimatePresence>
          </FadeIn>
        </section>

        {/* ── Video Tutorials ──────────────────────────────────── */}
        <section className="border-t border-border bg-background-secondary">
          <div className="mx-auto max-w-7xl px-6 py-24">
            <FadeIn>
              <div className="text-center mb-16">
                <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-primary/10 px-4 py-2 text-sm font-medium text-primary">
                  <Video className="h-4 w-4" />
                  Video Tutorials
                </div>
                <h2 className="font-display text-3xl font-bold text-foreground sm:text-4xl">
                  Learn with{" "}
                  <span className="gradient-text">video guides</span>
                </h2>
                <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                  Watch step-by-step video tutorials from our team. Perfect
                  for visual learners who want to master Velocira quickly.
                </p>
              </div>
            </FadeIn>

            <StaggerContainer className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {videoTutorials.map((video, idx) => (
                <StaggerItem key={idx}>
                  <Card hover className="group overflow-hidden">
                    {/* Thumbnail placeholder */}
                    <div className="relative -mx-6 -mt-6 mb-5 aspect-video bg-gradient-to-br from-primary/10 via-accent/5 to-primary/5 flex flex-col items-center justify-center border-b border-border overflow-hidden">
                      <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_50%,rgba(var(--primary-rgb),0.08),transparent_70%)]" />
                      <motion.div
                        whileHover={{ scale: 1.1 }}
                        className="relative z-10 flex h-14 w-14 items-center justify-center rounded-full bg-primary/20 backdrop-blur-sm border border-primary/30 group-hover:bg-primary/30 transition-colors cursor-pointer"
                      >
                        <CirclePlay className="h-8 w-8 text-primary" />
                      </motion.div>
                      <p className="relative z-10 mt-3 text-xs text-foreground-secondary max-w-[200px] text-center px-4">
                        {video.thumbnail}
                      </p>

                      {/* Duration badge */}
                      <span className="absolute bottom-3 right-3 rounded-lg bg-black/70 px-2 py-1 text-xs font-mono text-white backdrop-blur-sm">
                        {video.duration}
                      </span>
                    </div>

                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <span className="text-xs font-medium text-primary">
                          {video.category}
                        </span>
                        <h3 className="mt-1 font-semibold text-foreground group-hover:text-primary transition-colors">
                          {video.title}
                        </h3>
                      </div>
                      <Play className="h-4 w-4 shrink-0 text-foreground-secondary/30 mt-1" />
                    </div>
                  </Card>
                </StaggerItem>
              ))}
            </StaggerContainer>

            <FadeIn delay={0.2}>
              <p className="mt-8 text-center text-sm text-foreground-secondary">
                More tutorials coming soon.{" "}
                <Link
                  href="/register"
                  className="text-primary hover:underline"
                >
                  Sign up
                </Link>{" "}
                to get notified when new content drops.
              </p>
            </FadeIn>
          </div>
        </section>

        {/* ── FAQ ──────────────────────────────────────────────── */}
        <section className="mx-auto max-w-4xl px-6 py-24">
          <FadeIn>
            <div className="text-center mb-16">
              <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-primary/10 px-4 py-2 text-sm font-medium text-primary">
                <HelpCircle className="h-4 w-4" />
                FAQ
              </div>
              <h2 className="font-display text-3xl font-bold text-foreground sm:text-4xl">
                Frequently asked{" "}
                <span className="gradient-text">questions</span>
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                Can&apos;t find what you&apos;re looking for? Reach out to our
                support team — we&apos;re happy to help.
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="space-y-3">
            {faqItems.map((item, idx) => (
              <StaggerItem key={idx}>
                <FAQItem
                  question={item.question}
                  answer={item.answer}
                  isOpen={openFAQ === idx}
                  onToggle={() =>
                    setOpenFAQ(openFAQ === idx ? null : idx)
                  }
                />
              </StaggerItem>
            ))}
          </StaggerContainer>
        </section>

        {/* ── CTA ──────────────────────────────────────────────── */}
        <section className="border-t border-border">
          <div className="mx-auto max-w-7xl px-6 py-24">
            <FadeIn>
              <Card className="relative overflow-hidden text-center p-12 lg:p-16">
                {/* Background decoration */}
                <div className="pointer-events-none absolute inset-0">
                  
                  
                </div>

                <div className="relative z-10">
                  <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-primary/10 px-4 py-2 text-sm font-medium text-primary">
                    <Sparkles className="h-4 w-4" />
                    Ready to start?
                  </div>
                  <h2 className="font-display text-3xl font-bold text-foreground sm:text-4xl">
                    Still have questions?
                  </h2>
                  <p className="mt-4 text-lg text-foreground-secondary max-w-xl mx-auto">
                    Create a free account and explore every feature hands-on.
                    Our support team is always available to assist you.
                  </p>
                  <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
                    <Link href="/register">
                      <Button
                        size="lg"
                        icon={<ArrowRight className="h-5 w-5" />}
                      >
                        Get Started Free
                      </Button>
                    </Link>
                    <Link href="/contact">
                      <Button
                        size="lg"
                        variant="outline"
                        icon={<ExternalLink className="h-5 w-5" />}
                      >
                        Contact Support
                      </Button>
                    </Link>
                  </div>
                </div>
              </Card>
            </FadeIn>
          </div>
        </section>
      </div>
    </PageTransition>
  );
}
