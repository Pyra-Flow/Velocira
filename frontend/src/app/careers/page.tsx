"use client";

import Link from "next/link";

import {
  Sparkles,
  TrendingUp,
  Target,
  Heart,
  MapPin,
  Clock,
  Building2,
  Briefcase,
  GraduationCap,
  HeartPulse,
  PiggyBank,
  Timer,
  Palmtree,
  Wifi,
  ArrowRight,
  Mail,
  Users,
  ChevronRight,
} from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import { FadeIn, StaggerContainer, StaggerItem, PageTransition } from "@/components/ui/Animations";

/* ------------------------------------------------------------------ */
/*  Data                                                               */
/* ------------------------------------------------------------------ */

const values = [
  {
    icon: Sparkles,
    title: "Innovation",
    description:
      "We push the boundaries of what AI can do for software teams. Bold ideas are celebrated, experimentation is encouraged, and every voice shapes the product.",
    color: "text-primary",
    bg: "bg-primary/10",
  },
  {
    icon: TrendingUp,
    title: "Growth",
    description:
      "Personal development is a priority, not an afterthought. We invest in learning budgets, mentorship, and career progression frameworks for every team member.",
    color: "text-accent",
    bg: "bg-accent/10",
  },
  {
    icon: Target,
    title: "Impact",
    description:
      "Every line of code, every design, and every decision directly impacts thousands of developers worldwide. Your work here matters and reaches real users from day one.",
    color: "text-info",
    bg: "bg-info/10",
  },
  {
    icon: Heart,
    title: "Balance",
    description:
      "We believe great work comes from well-rested minds. Flexible schedules, generous PTO, and a culture that respects boundaries are non-negotiable parts of our DNA.",
    color: "text-success",
    bg: "bg-success/10",
  },
];

const positions = [
  {
    title: "Senior Full-Stack Engineer",
    location: "Cairo, Egypt",
    type: "Full-time",
    department: "Engineering",
    description:
      "Lead the development of core platform features across our Next.js frontend and Spring Boot backend. You'll architect scalable solutions, mentor junior engineers, and drive technical decisions that shape the product.",
    requirements: [
      "5+ years with TypeScript, React/Next.js, and Java/Spring Boot",
      "Experience with PostgreSQL, Redis, and cloud infrastructure (AWS/GCP)",
      "Strong system design and API architecture skills",
    ],
  },
  {
    title: "ML Engineer",
    location: "Remote",
    type: "Full-time",
    department: "AI & Machine Learning",
    description:
      "Design, train, and deploy the AI models that power Velocira's documentation generation engine. You'll work on fine-tuning LLMs, building RAG pipelines, and optimizing inference for real-time document generation.",
    requirements: [
      "3+ years in ML engineering with focus on NLP/LLMs",
      "Proficiency with Python, PyTorch/TensorFlow, and Hugging Face",
      "Experience with model deployment, optimization, and MLOps",
    ],
  },
  {
    title: "Product Designer",
    location: "Remote",
    type: "Full-time",
    department: "Design",
    description:
      "Own the end-to-end design experience for Velocira — from user research and wireframes to pixel-perfect UI. You'll collaborate closely with engineering and product to create intuitive, beautiful interfaces.",
    requirements: [
      "4+ years of product design experience (B2B SaaS preferred)",
      "Expert proficiency in Figma with a strong portfolio",
      "Experience with design systems, user research, and accessibility",
    ],
  },
  {
    title: "DevOps Engineer",
    location: "Cairo, Egypt",
    type: "Full-time",
    department: "Infrastructure",
    description:
      "Build and maintain the infrastructure that keeps Velocira fast, reliable, and secure. You'll manage CI/CD pipelines, Kubernetes clusters, monitoring systems, and ensure 99.9% uptime for our global user base.",
    requirements: [
      "3+ years in DevOps/SRE with Kubernetes and Docker",
      "Experience with AWS/GCP, Terraform, and GitHub Actions",
      "Strong knowledge of monitoring (Prometheus, Grafana) and security best practices",
    ],
  },
];

const benefits = [
  {
    icon: Wifi,
    title: "Remote-Friendly",
    description: "Work from anywhere — home, café, or co-working space. We support async collaboration across time zones.",
  },
  {
    icon: GraduationCap,
    title: "Learning Budget",
    description: "$2,000/year for courses, conferences, books, and certifications. Invest in your continuous growth.",
  },
  {
    icon: HeartPulse,
    title: "Health Insurance",
    description: "Comprehensive medical, dental, and vision coverage for you and your dependents, fully covered by the company.",
  },
  {
    icon: PiggyBank,
    title: "Equity",
    description: "All full-time employees receive stock options. Grow with the company and share in our success together.",
  },
  {
    icon: Timer,
    title: "Flexible Hours",
    description: "Core hours overlap for collaboration, but you structure your day around your peak productivity times.",
  },
  {
    icon: Palmtree,
    title: "Team Retreats",
    description: "Annual team off-sites in inspiring locations. Connect in person, brainstorm, and recharge together.",
  },
];

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function CareersPage() {
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
              We&apos;re Hiring
            </div>

            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold font-display text-foreground">
              Join the Future of{" "}
              <span className="gradient-text">Documentation</span>
            </h1>

            <p className="mt-6 text-lg sm:text-xl text-foreground-secondary max-w-2xl mx-auto leading-relaxed">
              Help us build the AI platform that transforms how developers create and manage
              software documentation. Work with cutting-edge tech, brilliant people, and real impact.
            </p>

            <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
              <a href="#positions">
                <Button size="lg" icon={<Briefcase className="h-5 w-5" />}>
                  View Open Positions
                </Button>
              </a>
              <Link href="/about">
                <Button variant="outline" size="lg" icon={<Users className="h-5 w-5" />}>
                  About Our Team
                </Button>
              </Link>
            </div>
          </FadeIn>
        </div>
      </section>

      {/* ───────── Culture & Values ───────── */}
      <section className="py-20 sm:py-28 bg-background-secondary">
        <div className="max-w-6xl mx-auto px-4">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-4xl font-bold font-display text-foreground">
                What Drives <span className="gradient-text">Us</span>
              </h2>
              <p className="mt-4 text-foreground-secondary max-w-xl mx-auto">
                Our culture is built on four core values that guide every decision we make —
                from product strategy to how we treat each other.
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {values.map((value) => {
              const Icon = value.icon;
              return (
                <StaggerItem key={value.title}>
                  <Card hover className="h-full text-center">
                    <div className={`inline-flex p-4 rounded-2xl ${value.bg} mb-5`}>
                      <Icon className={`h-7 w-7 ${value.color}`} />
                    </div>
                    <h3 className="text-lg font-bold font-display text-foreground mb-3">
                      {value.title}
                    </h3>
                    <p className="text-sm text-foreground-secondary leading-relaxed">
                      {value.description}
                    </p>
                  </Card>
                </StaggerItem>
              );
            })}
          </StaggerContainer>
        </div>
      </section>

      {/* ───────── Open Positions ───────── */}
      <section id="positions" className="py-20 sm:py-28 scroll-mt-20">
        <div className="max-w-4xl mx-auto px-4">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-4xl font-bold font-display text-foreground">
                Open <span className="gradient-text">Positions</span>
              </h2>
              <p className="mt-4 text-foreground-secondary max-w-xl mx-auto">
                We&apos;re looking for talented individuals who are passionate about AI,
                developer tools, and building products that make a difference.
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="space-y-6">
            {positions.map((position) => (
              <StaggerItem key={position.title}>
                <Card hover className="group">
                  <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 mb-4">
                    <div>
                      <h3 className="text-xl font-bold font-display text-foreground group-hover:text-primary transition-colors">
                        {position.title}
                      </h3>
                      <div className="flex flex-wrap items-center gap-3 mt-2">
                        <span className="inline-flex items-center gap-1.5 text-sm text-foreground-secondary">
                          <MapPin className="h-3.5 w-3.5" />
                          {position.location}
                        </span>
                        <span className="inline-flex items-center gap-1.5 text-sm text-foreground-secondary">
                          <Clock className="h-3.5 w-3.5" />
                          {position.type}
                        </span>
                        <span className="inline-flex items-center gap-1.5 text-sm text-foreground-secondary">
                          <Building2 className="h-3.5 w-3.5" />
                          {position.department}
                        </span>
                      </div>
                    </div>
                    <Link href={`/contact?subject=Application: ${encodeURIComponent(position.title)}`}>
                      <Button
                        size="sm"
                        icon={<ArrowRight className="h-4 w-4" />}
                      >
                        Apply Now
                      </Button>
                    </Link>
                  </div>

                  <p className="text-foreground-secondary text-sm leading-relaxed mb-4">
                    {position.description}
                  </p>

                  <div className="border-t border-border pt-4">
                    <p className="text-xs font-medium text-foreground-secondary/70 uppercase tracking-wider mb-2">
                      Key Requirements
                    </p>
                    <ul className="space-y-1.5">
                      {position.requirements.map((req, i) => (
                        <li key={i} className="flex items-start gap-2 text-sm text-foreground-secondary">
                          <ChevronRight className="h-4 w-4 text-primary flex-shrink-0 mt-0.5" />
                          {req}
                        </li>
                      ))}
                    </ul>
                  </div>
                </Card>
              </StaggerItem>
            ))}
          </StaggerContainer>
        </div>
      </section>

      {/* ───────── Benefits ───────── */}
      <section className="py-20 sm:py-28 bg-background-secondary">
        <div className="max-w-6xl mx-auto px-4">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-4xl font-bold font-display text-foreground">
                Perks & <span className="gradient-text">Benefits</span>
              </h2>
              <p className="mt-4 text-foreground-secondary max-w-xl mx-auto">
                We believe taking care of our team is the foundation for building an exceptional product.
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {benefits.map((benefit) => {
              const Icon = benefit.icon;
              return (
                <StaggerItem key={benefit.title}>
                  <Card hover className="h-full">
                    <div className="flex items-start gap-4">
                      <div className="flex-shrink-0 p-3 rounded-xl bg-primary/10">
                        <Icon className="h-6 w-6 text-primary" />
                      </div>
                      <div>
                        <h3 className="text-base font-bold font-display text-foreground mb-1.5">
                          {benefit.title}
                        </h3>
                        <p className="text-sm text-foreground-secondary leading-relaxed">
                          {benefit.description}
                        </p>
                      </div>
                    </div>
                  </Card>
                </StaggerItem>
              );
            })}
          </StaggerContainer>
        </div>
      </section>

      {/* ───────── Bottom CTA ───────── */}
      <section className="relative overflow-hidden py-24 sm:py-32">
        

        <div className="relative max-w-3xl mx-auto px-4 text-center">
          <FadeIn>
            <h2 className="text-3xl sm:text-4xl font-bold font-display text-foreground">
              Don&apos;t See Your <span className="gradient-text">Role</span>?
            </h2>

            <p className="mt-6 text-lg text-foreground-secondary max-w-xl mx-auto leading-relaxed">
              We&apos;re always on the lookout for exceptional talent. If you believe you&apos;d be a great
              fit for Velocira, we&apos;d love to hear from you.
            </p>

            <div className="mt-10 flex flex-wrap items-center justify-center gap-4">
              <Link href="/contact?subject=Open Application">
                <Button size="lg" icon={<Mail className="h-5 w-5" />}>
                  Send Us Your Resume
                </Button>
              </Link>
              <Link href="/">
                <Button variant="ghost" size="lg" icon={<ChevronRight className="h-5 w-5" />}>
                  Learn More About Velocira
                </Button>
              </Link>
            </div>

            <p className="mt-6 text-sm text-foreground-secondary/60">
              We review every application · Typical response within 5 business days
            </p>
          </FadeIn>
        </div>
      </section>
    </PageTransition>
  );
}
