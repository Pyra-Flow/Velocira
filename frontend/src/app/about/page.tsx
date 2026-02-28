"use client";

import { useRef } from "react";
import Link from "next/link";
import { motion, useInView } from "framer-motion";
import {
  Target,
  BookOpen,
  Users,
  Lightbulb,
  Award,
  Heart,
  Eye,
  Rocket,
  Code2,
  Brain,
  Server,
  Database,
  Globe,
  Github,
  Linkedin,
  Twitter,
  ArrowRight,
  Sparkles,
  Infinity,
  FileText,
  Container,
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

const teamMembers = [
  {
    name: "Omar Elrfaay",
    initials: "OE",
    role: "Co-Founder & Lead Engineer",
    bio: "SDET turned Tech Lead with deep expertise in Java, Spring Boot, and scalable backend architectures. Passionate about building robust systems that just work.",
    color: "from-violet-500 to-purple-600",
    github: "https://github.com",
    linkedin: "https://linkedin.com",
    twitter: "https://twitter.com",
  },
  {
    name: "Omar Abdelhamid",
    initials: "OA",
    role: "Co-Founder & ML Engineer",
    bio: "AI/ML specialist with a focus on large language models and natural language processing. Bridges the gap between cutting-edge research and production-ready AI systems.",
    color: "from-purple-500 to-indigo-600",
    github: "https://github.com",
    linkedin: "https://linkedin.com",
    twitter: "https://twitter.com",
  },
  {
    name: "Omar Wageh",
    initials: "OW",
    role: "Co-Founder & ML Engineer",
    bio: "Data scientist specializing in NLP and model optimization. Ensures our AI pipeline delivers fast, accurate documentation every single time.",
    color: "from-indigo-500 to-blue-600",
    github: "https://github.com",
    linkedin: "https://linkedin.com",
    twitter: "https://twitter.com",
  },
];

const timeline = [
  {
    date: "Jan 2026",
    title: "Idea Conceived",
    description:
      "Three engineers frustrated with documentation decided enough was enough. The idea for Velocira was born over late-night brainstorming sessions.",
    icon: Lightbulb,
  },
  {
    date: "Feb 2026",
    title: "SRS Completed, Development Begins",
    description:
      "Full Software Requirements Specification finalized. Architecture designed, tech stack chosen, and the first lines of code were written.",
    icon: FileText,
  },
  {
    date: "Mar 2026",
    title: "Backend Auth System Complete",
    description:
      "JWT-based authentication, email verification, password recovery — the secure foundation of the platform was locked in.",
    icon: Server,
  },
  {
    date: "Apr 2026",
    title: "ML Pipeline Prototype",
    description:
      "First working prototype of the AI documentation engine. LLM integration, prompt engineering, and document generation pipeline operational.",
    icon: Brain,
  },
  {
    date: "May 2026",
    title: "Beta Launch",
    description:
      "Private beta opened to early adopters. Real-world feedback began shaping the product into something truly useful.",
    icon: Rocket,
  },
  {
    date: "Jun 2026",
    title: "Public Launch",
    description:
      "Velocira goes live. AI-powered documentation for everyone — from solo developers to enterprise teams.",
    icon: Sparkles,
  },
];

const values = [
  {
    key: "innovation",
    icon: Lightbulb,
    title: "Innovation First",
    description:
      "Pushing boundaries with cutting-edge AI and modern engineering. We don't follow trends — we set them.",
  },
  {
    key: "quality",
    icon: Award,
    title: "Quality Obsessed",
    description:
      "Every generated document meets production-ready standards. We sweat the details so you don't have to.",
  },
  {
    key: "community",
    icon: Heart,
    title: "Community Driven",
    description:
      "Built for users, evolving based on your feedback. Our roadmap is shaped by the people who use Velocira daily.",
  },
  {
    key: "transparency",
    icon: Eye,
    title: "Open & Transparent",
    description:
      "Open development process, clear communication, and honest pricing. No surprises, no hidden agendas.",
  },
];

const stats = [
  { value: "3", label: "Co-Founders", icon: Users },
  { value: "1", label: "Platform", icon: Globe },
  { value: "6", label: "Document Types", icon: FileText },
  { value: "∞", label: "Possibilities", icon: Infinity },
];

const techStack = [
  { name: "Spring Boot", icon: Server, color: "text-green-400" },
  { name: "Next.js", icon: Globe, color: "text-foreground" },
  { name: "Python", icon: Code2, color: "text-yellow-400" },
  { name: "PostgreSQL", icon: Database, color: "text-blue-400" },
  { name: "Docker", icon: Container, color: "text-cyan-400" },
  { name: "Hugging Face", icon: Brain, color: "text-amber-400" },
  { name: "LangChain", icon: Sparkles, color: "text-purple-400" },
  { name: "Redis", icon: Database, color: "text-red-400" },
];

/* ------------------------------------------------------------------ */
/*  Animated Counter                                                   */
/* ------------------------------------------------------------------ */

function AnimatedStat({
  value,
  label,
  icon: Icon,
}: {
  value: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}) {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true });

  return (
    <motion.div
      ref={ref}
      initial={{ opacity: 0, scale: 0.8 }}
      animate={isInView ? { opacity: 1, scale: 1 } : {}}
      transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
    >
      <Card hover glow className="text-center py-8">
        <div className="h-12 w-12 rounded-xl bg-primary/10 flex items-center justify-center mx-auto mb-4">
          <Icon className="h-6 w-6 text-primary" />
        </div>
        <p className="text-4xl sm:text-5xl font-bold font-display gradient-text mb-2">
          {value}
        </p>
        <p className="text-sm text-foreground-secondary font-medium">
          {label}
        </p>
      </Card>
    </motion.div>
  );
}

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */

export default function AboutPage() {
  const { t } = useLocale();

  return (
    <PageTransition>
      {/* ============================================================ */}
      {/* Hero                                                         */}
      {/* ============================================================ */}
      <section className="relative overflow-hidden py-28 sm:py-36">
        
        
        <div className="absolute top-20 right-[8%] w-28 h-28 border border-primary/[0.06] rotate-45 pointer-events-none hidden lg:block" />
        <div className="absolute bottom-20 left-[6%] w-16 h-16 border border-primary/[0.05] rotate-45 pointer-events-none hidden lg:block" />

        <div className="relative max-w-4xl mx-auto px-4 text-center">
          <FadeIn>
            <div className="inline-flex items-center gap-2 px-5 py-2 rounded-full border border-primary/20 bg-primary/[0.06] text-primary text-sm font-medium mb-8">
              <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
              PyraFlow
            </div>
            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold font-display">
              <span className="gradient-text">About Velocira</span>
            </h1>
            <p className="mt-6 text-lg sm:text-xl text-foreground-secondary max-w-2xl mx-auto leading-relaxed">
              The future of project documentation is here. AI-powered, developer-loved,
              and built to transform how teams create and manage technical documents.
            </p>
          </FadeIn>
        </div>
      </section>

      {/* ============================================================ */}
      {/* Mission                                                      */}
      {/* ============================================================ */}
      <section className="py-24 bg-card/30">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
          <FadeIn>
            <div className="text-center">
              <div className="h-16 w-16 rounded-2xl bg-primary/10 flex items-center justify-center mx-auto mb-8">
                <Target className="h-8 w-8 text-primary" />
              </div>
              <h2 className="text-3xl sm:text-4xl font-bold font-display gradient-text mb-6">
                Our Mission
              </h2>
              <p className="text-xl sm:text-2xl text-foreground leading-relaxed max-w-3xl mx-auto font-medium">
                &ldquo;We believe documentation should be a{" "}
                <span className="text-primary">catalyst</span>, not a bottleneck.&rdquo;
              </p>
              <p className="mt-6 text-lg text-foreground-secondary max-w-2xl mx-auto leading-relaxed">
                Velocira exists to free developers from the tedium of manual documentation.
                We harness the power of AI to generate comprehensive, accurate, and
                beautiful project documents — so you can focus on building what matters.
              </p>
            </div>
          </FadeIn>
        </div>
      </section>

      {/* ============================================================ */}
      {/* Our Story                                                    */}
      {/* ============================================================ */}
      <section className="py-24">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid md:grid-cols-5 gap-12 items-center">
            <div className="md:col-span-3">
              <FadeIn direction="right">
                <div className="flex items-center gap-3 mb-6">
                  <div className="h-12 w-12 rounded-xl bg-primary/10 flex items-center justify-center">
                    <BookOpen className="h-6 w-6 text-primary" />
                  </div>
                  <h2 className="text-3xl sm:text-4xl font-bold font-display gradient-text">
                    Our Story
                  </h2>
                </div>
                <div className="space-y-4 text-foreground-secondary leading-relaxed text-lg">
                  <p>
                    Velocira was born out of pure frustration. Three software engineers
                    from Egypt, deep in the trenches of building projects, found
                    themselves spending more time writing documentation than writing code.
                  </p>
                  <p>
                    SRS documents, API specs, test plans — the same repetitive, tedious
                    work over and over again. They knew there had to be a better way.
                  </p>
                  <p>
                    In early 2026, what started as a graduation project quickly evolved
                    into something much bigger. The team combined their expertise in
                    backend engineering, machine learning, and NLP to build an AI-powered
                    platform that could generate production-ready documentation in seconds.
                  </p>
                  <p className="text-foreground font-medium">
                    Today, Velocira is a full SaaS product — and we&apos;re just getting
                    started.
                  </p>
                </div>
              </FadeIn>
            </div>

            <div className="md:col-span-2">
              <FadeIn direction="left">
                <Card glow className="p-8 text-center">
                  <div className="h-24 w-24 rounded-full bg-gradient-to-br from-violet-500 to-indigo-600 flex items-center justify-center mx-auto mb-6 shadow-lg shadow-primary/20">
                    <Rocket className="h-10 w-10 text-white" />
                  </div>
                  <p className="text-2xl font-bold font-display text-foreground mb-2">
                    Est. 2026
                  </p>
                  <p className="text-foreground-secondary">
                    Cairo, Egypt
                  </p>
                  <div className="mt-4 pt-4 border-t border-border">
                    <p className="text-sm text-foreground-secondary">
                      From graduation project to full SaaS platform
                    </p>
                  </div>
                </Card>
              </FadeIn>
            </div>
          </div>
        </div>
      </section>

      {/* ============================================================ */}
      {/* Timeline                                                     */}
      {/* ============================================================ */}
      <section className="py-24 bg-card/30">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
          <FadeIn>
            <div className="text-center mb-16">
              <div className="inline-flex items-center gap-2 px-5 py-2 rounded-full border border-primary/20 bg-primary/[0.06] text-primary text-sm font-medium mb-4">
                <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
                Milestones
              </div>
              <h2 className="text-3xl sm:text-4xl font-bold font-display gradient-text">
                Our Journey
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                From idea to launch — every step of the Velocira story.
              </p>
            </div>
          </FadeIn>

          <div className="relative">
            {/* Vertical line */}
            <div className="absolute start-6 md:start-1/2 top-0 bottom-0 w-px bg-border md:-translate-x-px" />

            <div className="space-y-12">
              {timeline.map((item, index) => {
                const Icon = item.icon;
                const isEven = index % 2 === 0;

                return (
                  <FadeIn
                    key={item.date}
                    delay={index * 0.1}
                    direction={isEven ? "right" : "left"}
                  >
                    <div className="relative flex items-start gap-6 md:gap-0">
                      {/* Dot */}
                      <div className="absolute start-6 md:start-1/2 -translate-x-1/2 z-10">
                        <div className="h-12 w-12 rounded-full bg-card border-2 border-primary flex items-center justify-center shadow-lg shadow-primary/10">
                          <Icon className="h-5 w-5 text-primary" />
                        </div>
                      </div>

                      {/* Content */}
                      <div
                        className={`ms-20 md:ms-0 md:w-1/2 ${
                          isEven
                            ? "md:pe-12 md:text-end"
                            : "md:ps-12 md:ms-auto"
                        }`}
                      >
                        <Card hover className="inline-block">
                          <p className="text-xs font-semibold text-primary uppercase tracking-wider mb-1">
                            {item.date}
                          </p>
                          <h3 className="text-lg font-bold text-foreground mb-2">
                            {item.title}
                          </h3>
                          <p className="text-sm text-foreground-secondary leading-relaxed">
                            {item.description}
                          </p>
                        </Card>
                      </div>
                    </div>
                  </FadeIn>
                );
              })}
            </div>
          </div>
        </div>
      </section>

      {/* ============================================================ */}
      {/* Team                                                         */}
      {/* ============================================================ */}
      <section className="py-24">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <FadeIn>
            <div className="text-center mb-16">
              <div className="inline-flex items-center gap-2 px-5 py-2 rounded-full border border-primary/20 bg-primary/[0.06] text-primary text-sm font-medium mb-4">
                <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
                The Founders
              </div>
              <h2 className="text-3xl sm:text-4xl font-bold font-display gradient-text">
                Meet the Team
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                Three engineers, one shared vision — making documentation effortless.
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="grid grid-cols-1 md:grid-cols-3 gap-8 max-w-5xl mx-auto">
            {teamMembers.map((member) => (
              <StaggerItem key={member.name}>
                <Card hover glow className="text-center group h-full flex flex-col">
                  <div className="flex-1">
                    <motion.div
                      whileHover={{ scale: 1.05, rotate: 3 }}
                      className={`h-24 w-24 rounded-full bg-gradient-to-br ${member.color} flex items-center justify-center mx-auto mb-5 shadow-lg shadow-primary/10`}
                    >
                      <span className="text-3xl font-bold text-white font-display">
                        {member.initials}
                      </span>
                    </motion.div>

                    <h3 className="text-xl font-bold text-foreground mb-1">
                      {member.name}
                    </h3>
                    <p className="text-sm font-semibold text-primary mb-4">
                      {member.role}
                    </p>
                    <p className="text-sm text-foreground-secondary leading-relaxed mb-6 px-2">
                      {member.bio}
                    </p>
                  </div>

                  {/* Social Links */}
                  <div className="flex items-center justify-center gap-3 pt-4 border-t border-border">
                    <a
                      href={member.github}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center text-foreground-secondary hover:text-primary hover:bg-primary/20 transition-colors"
                    >
                      <Github className="h-4 w-4" />
                    </a>
                    <a
                      href={member.linkedin}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center text-foreground-secondary hover:text-primary hover:bg-primary/20 transition-colors"
                    >
                      <Linkedin className="h-4 w-4" />
                    </a>
                    <a
                      href={member.twitter}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center text-foreground-secondary hover:text-primary hover:bg-primary/20 transition-colors"
                    >
                      <Twitter className="h-4 w-4" />
                    </a>
                  </div>
                </Card>
              </StaggerItem>
            ))}
          </StaggerContainer>
        </div>
      </section>

      {/* ============================================================ */}
      {/* Values                                                       */}
      {/* ============================================================ */}
      <section className="py-24 bg-card/30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-4xl font-bold font-display gradient-text">
                What We Stand For
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                The principles that guide every decision we make.
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {values.map(({ key, icon: Icon, title, description }) => (
              <StaggerItem key={key}>
                <Card hover className="text-center h-full">
                  <div className="h-14 w-14 rounded-2xl bg-primary/10 flex items-center justify-center mx-auto mb-4">
                    <Icon className="h-7 w-7 text-primary" />
                  </div>
                  <h3 className="text-lg font-semibold text-foreground mb-2">
                    {title}
                  </h3>
                  <p className="text-sm text-foreground-secondary leading-relaxed">
                    {description}
                  </p>
                </Card>
              </StaggerItem>
            ))}
          </StaggerContainer>
        </div>
      </section>

      {/* ============================================================ */}
      {/* Numbers                                                      */}
      {/* ============================================================ */}
      <section className="py-24">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-4xl font-bold font-display gradient-text">
                Velocira in Numbers
              </h2>
            </div>
          </FadeIn>

          <div className="grid grid-cols-2 lg:grid-cols-4 gap-6">
            {stats.map((stat, index) => (
              <AnimatedStat
                key={stat.label}
                value={stat.value}
                label={stat.label}
                icon={stat.icon}
              />
            ))}
          </div>
        </div>
      </section>

      {/* ============================================================ */}
      {/* Tech Stack                                                   */}
      {/* ============================================================ */}
      <section className="py-24 bg-card/30">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
          <FadeIn>
            <div className="text-center mb-16">
              <div className="inline-flex items-center gap-2 px-5 py-2 rounded-full border border-primary/20 bg-primary/[0.06] text-primary text-sm font-medium mb-4">
                <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
                Technology
              </div>
              <h2 className="text-3xl sm:text-4xl font-bold font-display gradient-text">
                Built With the Best
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                A modern tech stack chosen for performance, scalability, and developer experience.
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            {techStack.map(({ name, icon: Icon, color }) => (
              <StaggerItem key={name}>
                <Card hover className="text-center py-6 group">
                  <motion.div
                    whileHover={{ y: -4 }}
                    transition={{ duration: 0.2 }}
                  >
                    <Icon
                      className={`h-8 w-8 mx-auto mb-3 ${color} transition-colors group-hover:text-primary`}
                    />
                    <p className="text-sm font-medium text-foreground">
                      {name}
                    </p>
                  </motion.div>
                </Card>
              </StaggerItem>
            ))}
          </StaggerContainer>
        </div>
      </section>

      {/* ============================================================ */}
      {/* CTA                                                          */}
      {/* ============================================================ */}
      <section className="py-28">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <FadeIn>
            <div className="glass rounded-3xl p-10 sm:p-14 relative overflow-hidden">
              <div className="absolute inset-0 bg-gradient-to-br from-primary/5 via-transparent to-primary/5" />

              <div className="relative">
                <h2 className="text-3xl sm:text-4xl font-bold font-display gradient-text mb-4">
                  Join Us on This Journey
                </h2>
                <p className="text-lg text-foreground-secondary max-w-xl mx-auto mb-8 leading-relaxed">
                  Whether you&apos;re a solo developer or part of a large team,
                  Velocira is here to revolutionize how you create documentation.
                  Be part of the future.
                </p>

                <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
                  <Link href="/register">
                    <Button
                      size="lg"
                      icon={<ArrowRight className="h-5 w-5" />}
                    >
                      Get Started Free
                    </Button>
                  </Link>
                  <Link href="/contact">
                    <Button variant="outline" size="lg">
                      Contact Us
                    </Button>
                  </Link>
                </div>
              </div>
            </div>
          </FadeIn>
        </div>
      </section>
    </PageTransition>
  );
}
