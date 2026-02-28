"use client";

import { useState, useEffect, useRef } from "react";
import Link from "next/link";
import {
  motion,
  useScroll,
  useTransform,
  useInView,
  AnimatePresence,
} from "framer-motion";
import {
  Zap,
  FileText,
  Users,
  Shield,
  Rocket,
  Layout,
  ArrowRight,
  Sparkles,
  Play,
  Terminal,
  CheckCircle2,
  Star,
  Globe,
  Database,
  Server,
  Code2,
  Layers,
  BarChart3,
  Clock,
  ChevronRight,
  Monitor,
  Cpu,
  Lock,
  Cloud,
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

/* ─────────────── Animated Counter Hook ─────────────── */
function useAnimatedCounter(target: number, duration = 2000, inView: boolean) {
  const [count, setCount] = useState(0);
  const hasAnimated = useRef(false);

  useEffect(() => {
    if (!inView || hasAnimated.current) return;
    hasAnimated.current = true;
    const start = performance.now();
    const step = (now: number) => {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setCount(Math.floor(eased * target));
      if (progress < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }, [inView, target, duration]);

  return count;
}

/* ─────────────── 3D Tilt Card Component ─────────────── */
function TiltCard({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [tilt, setTilt] = useState({ x: 0, y: 0 });
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    setIsMobile(window.matchMedia("(max-width: 768px)").matches || "ontouchstart" in window);
  }, []);

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!ref.current || isMobile) return;
    const rect = ref.current.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width - 0.5;
    const y = (e.clientY - rect.top) / rect.height - 0.5;
    setTilt({ x: y * -15, y: x * 15 });
  };

  const handleMouseLeave = () => setTilt({ x: 0, y: 0 });

  if (isMobile) {
    return <div className={className}>{children}</div>;
  }

  return (
    <motion.div
      ref={ref}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      animate={{ rotateX: tilt.x, rotateY: tilt.y }}
      transition={{ type: "spring", stiffness: 300, damping: 20 }}
      style={{ perspective: 1000, transformStyle: "preserve-3d", willChange: "transform" }}
      className={className}
    >
      {children}
    </motion.div>
  );
}

/* ─────────────── Data ─────────────── */
const featureData = [
  { icon: Zap, key: "ai", color: "from-yellow-500/20 to-orange-500/20" },
  { icon: FileText, key: "formats", color: "from-blue-500/20 to-cyan-500/20" },
  { icon: Users, key: "collaboration", color: "from-green-500/20 to-emerald-500/20" },
  { icon: Shield, key: "security", color: "from-red-500/20 to-pink-500/20" },
  { icon: Rocket, key: "speed", color: "from-purple-500/20 to-violet-500/20" },
  { icon: Layout, key: "templates", color: "from-indigo-500/20 to-blue-500/20" },
];

const howItWorks = [
  { icon: Terminal, title: "Describe Your Idea", desc: "Type a brief project description or paste your existing brief — Velocira understands context." },
  { icon: Layers, title: "Choose Project Type", desc: "Select from web app, mobile, API, ML pipeline, embedded, and more. Templates adapt automatically." },
  { icon: Cpu, title: "AI Generates Docs", desc: "Our multi-model pipeline produces SRS, ERD, API specs, use cases, and architecture in parallel." },
  { icon: Globe, title: "Export & Share", desc: "Download PDF, Markdown, or DOCX. Share live links with your team or stakeholders instantly." },
];

const docTypes = [
  { label: "SRS", title: "Software Requirements Specification", content: "FR-01 User Authentication\nFR-02 Role-Based Dashboard\nFR-03 Document Versioning\nFR-04 Real-time Collaboration\nNFR-01 Response Time < 200ms\nNFR-02 99.9% Uptime SLA" },
  { label: "Use Cases", title: "Use Case Diagrams & Flows", content: "UC-01 Register Account\n  Actor: End User\n  Precondition: Valid email\n  Flow: Fill form → Verify OTP → Dashboard\nUC-02 Generate Documentation\n  Actor: Project Owner\n  Flow: Describe → Configure → Generate" },
  { label: "ERD", title: "Entity Relationship Diagram", content: "┌─────────┐    ┌──────────┐\n│  User   │1──N│ Project  │\n└────┬────┘    └────┬─────┘\n     │              │1\n     │         ┌────┴─────┐\n     └────N───>│ Document │\n               └──────────┘" },
  { label: "API", title: "API Specification & Contracts", content: "POST /api/v1/auth/register\n  Body: { name, email, password }\n  201: { token, user }\nPOST /api/v1/projects\n  Auth: Bearer token\n  Body: { name, description, type }\n  201: { project }" },
  { label: "Architecture", title: "Architecture Decision Records", content: "ADR-001: Microservices Architecture\n  Status: Accepted\n  Context: Scale independently\n  Decision: Spring Boot + Next.js\nADR-002: Event-Driven Communication\n  Status: Accepted\n  Decision: Apache Kafka" },
  { label: "Roadmap", title: "Product Roadmap & Milestones", content: "Phase 1 (Q1): Core auth + generation\nPhase 2 (Q2): Team collaboration\nPhase 3 (Q3): Enterprise features\nPhase 4 (Q4): AI model fine-tuning\n\nMilestone: 10K users by Q2\nMilestone: SOC2 by Q3" },
];

const testimonials = [
  { name: "Sarah Chen", role: "CTO at TechForge", avatar: "SC", quote: "Velocira cut our documentation time by 90%. What used to take our team two weeks now takes five minutes. The AI understands context better than any tool we've tried." },
  { name: "Marcus Rivera", role: "Lead Developer at Nexus Labs", avatar: "MR", quote: "The generated SRS documents are incredibly thorough. Our clients are impressed by the quality and consistency. It's become essential to our workflow." },
  { name: "Aisha Patel", role: "Product Manager at CloudScale", avatar: "AP", quote: "As a PM, I love that I can describe a feature idea and get a complete specification back. The ERD generation alone has saved us hundreds of hours." },
];

const pricingTiers = [
  { name: "Free", price: "$0", period: "/month", features: ["Up to 3 projects", "Basic SRS generation", "PDF export", "Community support", "1 team member"], highlight: false },
  { name: "Pro", price: "$12", period: "/month", features: ["Unlimited projects", "All document types (SRS, ERD, API, Architecture)", "PDF, DOCX & Markdown export", "Priority support", "Up to 5 team members", "Custom templates", "API access"], highlight: true },
  { name: "Enterprise", price: "Custom", period: "", features: ["Everything in Pro", "Unlimited team members", "SSO / SAML authentication", "Dedicated account manager", "SLA guarantee (99.9%)", "Admin analytics dashboard"], highlight: false },
];

const techStack = [
  "Spring Boot", "Next.js", "React", "Python", "PostgreSQL", "Docker",
  "Kafka", "Redis", "TensorFlow", "TypeScript", "Tailwind CSS", "Kubernetes",
];

/* ─────────────── Demo Steps Content ─────────────── */
const demoTypingText = "An e-commerce platform with user auth, product catalog, shopping cart, payment processing, and admin dashboard...";

/* ─────────────── Main Component ─────────────── */
export default function LandingPage() {
  const { t } = useLocale();
  const { scrollYProgress } = useScroll();
  const heroY = useTransform(scrollYProgress, [0, 0.3], [0, -120]);
  const heroScale = useTransform(scrollYProgress, [0, 0.15], [1, 0.95]);
  const heroOpacity = useTransform(scrollYProgress, [0, 0.2], [1, 0.6]);

  /* ─── Demo State ─── */
  const [demoStep, setDemoStep] = useState(0);
  const [demoPlaying, setDemoPlaying] = useState(false);
  const [typedText, setTypedText] = useState("");
  const [progress, setProgress] = useState(0);
  const [activeDocTab, setActiveDocTab] = useState(0);
  const demoRef = useRef<HTMLDivElement>(null);
  const demoInView = useInView(demoRef, { once: true, margin: "-100px" });

  const demoTimers = useRef<(ReturnType<typeof setTimeout> | ReturnType<typeof setInterval>)[]>([]);

  /* Auto-play demo on scroll */
  useEffect(() => {
    if (demoInView && !demoPlaying) startDemo();
    return () => {
      demoTimers.current.forEach((id) => clearTimeout(id as unknown as NodeJS.Timeout));
      demoTimers.current = [];
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [demoInView]);

  function startDemo() {
    setDemoPlaying(true);
    setDemoStep(0);
    setTypedText("");
    setProgress(0);

    /* Step 1: Typing */
    let charIdx = 0;
    const typeInterval = setInterval(() => {
      charIdx++;
      setTypedText(demoTypingText.slice(0, charIdx));
      if (charIdx >= demoTypingText.length) {
        clearInterval(typeInterval);
        const t1 = setTimeout(() => setDemoStep(1), 600);
        demoTimers.current.push(t1);
      }
    }, 30);
    demoTimers.current.push(typeInterval);

    /* Step 2: Select project type after typing */
    const t2 = setTimeout(() => {
      setDemoStep(2);
      /* Step 3: Progress bar */
      let prog = 0;
      const progInterval = setInterval(() => {
        prog += 2;
        setProgress(prog);
        if (prog >= 100) {
          clearInterval(progInterval);
          const t3 = setTimeout(() => setDemoStep(3), 400);
          demoTimers.current.push(t3);
        }
      }, 40);
      demoTimers.current.push(progInterval);
    }, demoTypingText.length * 30 + 1800);
    demoTimers.current.push(t2);
  }

  /* Auto-cycle doc tabs */
  useEffect(() => {
    const interval = setInterval(() => {
      setActiveDocTab((prev) => (prev + 1) % docTypes.length);
    }, 4000);
    return () => clearInterval(interval);
  }, []);

  /* Stats refs */
  const statsRef = useRef<HTMLDivElement>(null);
  const statsInView = useInView(statsRef, { once: true, margin: "-80px" });
  const statDocs = useAnimatedCounter(50000, 2000, statsInView);
  const statUsers = useAnimatedCounter(10000, 2000, statsInView);
  const statTime = useAnimatedCounter(5, 1500, statsInView);
  const statSatisfaction = useAnimatedCounter(98, 2000, statsInView);

  /* Parallax for How It Works */
  const howRef = useRef<HTMLDivElement>(null);
  const { scrollYProgress: howScrollProgress } = useScroll({
    target: howRef,
    offset: ["start end", "end start"],
  });
  const howY = useTransform(howScrollProgress, [0, 1], [60, -60]);

  return (
    <PageTransition>
      {/* ════════════════════════════════════════════════════════════
          SECTION 1 — HERO
          ════════════════════════════════════════════════════════════ */}
      <section className="relative -mt-24 min-h-[100vh] pt-24 flex items-center overflow-x-hidden">
        {/* Geometric accent shapes */}
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          {/* Large diamond — top right */}
          <div className="absolute -top-12 right-[8%] w-48 h-48 border border-primary/[0.07] rotate-45 hidden lg:block" />
          <div className="absolute -top-6 right-[10%] w-32 h-32 border border-primary/[0.05] rotate-45 hidden lg:block" />
          {/* Small diamond — bottom left */}
          <div className="absolute bottom-[15%] left-[5%] w-20 h-20 border border-primary/[0.06] rotate-45 hidden lg:block" />
          {/* Thin horizontal lines */}
          <div className="absolute top-[30%] left-0 w-[120px] h-px bg-gradient-to-r from-transparent via-primary/10 to-transparent hidden lg:block" />
          <div className="absolute top-[60%] right-0 w-[180px] h-px bg-gradient-to-l from-transparent via-primary/10 to-transparent hidden lg:block" />
          {/* Subtle radial glow */}
          
        </div>

        <motion.div
          style={{ y: heroY, scale: heroScale, opacity: heroOpacity, willChange: "transform" }}
          className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20 w-full"
        >
          <div className="text-center max-w-5xl mx-auto">
            <FadeIn delay={0.1}>
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/[0.06] px-5 py-2 text-sm text-primary mb-10"
              >
                <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
                <span className="font-medium tracking-wide">AI-Powered Documentation Platform</span>
              </motion.div>
            </FadeIn>

            <FadeIn delay={0.2}>
              <h1 className="text-4xl sm:text-6xl lg:text-[4.5rem] font-extrabold font-display leading-[1.05] tracking-[-0.03em]">
                <span className="text-foreground">Generate </span>
                <span className="relative inline-block">
                  <span className="gradient-text">Production-Ready</span>
                  <motion.div
                    initial={{ scaleX: 0 }}
                    animate={{ scaleX: 1 }}
                    transition={{ delay: 0.8, duration: 0.6, ease: "easeOut" }}
                    className="absolute -bottom-1 left-0 right-0 h-[3px] bg-primary/20 origin-left"
                  />
                </span>
                <br className="hidden sm:block" />
                <span className="text-foreground"> Docs in Minutes</span>
              </h1>
            </FadeIn>

            <FadeIn delay={0.35}>
              <p className="mt-8 text-base sm:text-lg lg:text-xl text-foreground-secondary max-w-2xl mx-auto leading-relaxed">
                {t("landing.subtitle")}
              </p>
            </FadeIn>

            <FadeIn delay={0.5}>
              <div className="mt-12 flex flex-col sm:flex-row items-center justify-center gap-4">
                <Link href="/register">
                  <Button size="lg" icon={<Zap className="h-5 w-5" />}>
                    {t("landing.cta")}
                  </Button>
                </Link>
                <a href="#demo">
                  <Button variant="outline" size="lg" icon={<Play className="h-5 w-5" />}>
                    Watch Demo
                  </Button>
                </a>
              </div>
            </FadeIn>

            {/* Trust indicators */}
            <FadeIn delay={0.65}>
              <div className="mt-14 flex flex-wrap items-center justify-center gap-6 text-xs text-foreground-secondary">
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 className="h-3.5 w-3.5 text-success" />
                  <span>No credit card required</span>
                </div>
                <div className="w-px h-3 bg-border hidden sm:block" />
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 className="h-3.5 w-3.5 text-success" />
                  <span>Setup in 60 seconds</span>
                </div>
                <div className="w-px h-3 bg-border hidden sm:block" />
                <div className="flex items-center gap-1.5">
                  <Star className="h-3.5 w-3.5 fill-yellow-500 text-yellow-500" />
                  <span>4.9/5 from 2,000+ devs</span>
                </div>
              </div>
            </FadeIn>
          </div>

          {/* Floating 3D Cards around Hero */}
          <div className="relative mt-20 h-72 hidden lg:block" style={{ perspective: "1200px" }}>
            {/* Card 1 — Top Left */}
            <motion.div
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, delay: 0.5, ease: "easeOut" }}
              className="absolute -top-8 left-0 w-64"
            >
              <Card glow className="glass backdrop-blur-xl">
                <div className="flex items-center gap-3 mb-2">
                  <div className="h-8 w-8 rounded-lg bg-primary/10 flex items-center justify-center">
                    <FileText className="h-4 w-4 text-primary" />
                  </div>
                  <span className="text-xs font-semibold text-foreground">SRS Document</span>
                </div>
                <div className="space-y-1.5">
                  <div className="h-2 rounded bg-primary/20 w-full" />
                  <div className="h-2 rounded bg-primary/15 w-4/5" />
                  <div className="h-2 rounded bg-primary/10 w-3/5" />
                </div>
                <span className="mt-3 inline-flex items-center text-[10px] text-success font-semibold">
                  <CheckCircle2 className="h-3 w-3 mr-1" /> Generated
                </span>
              </Card>
            </motion.div>

            {/* Card 2 — Top Right */}
            <motion.div
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, delay: 0.7, ease: "easeOut" }}
              className="absolute -top-4 right-0 w-60"
            >
              <Card glow className="glass backdrop-blur-xl">
                <div className="flex items-center gap-3 mb-2">
                  <div className="h-8 w-8 rounded-lg bg-green-500/10 flex items-center justify-center">
                    <BarChart3 className="h-4 w-4 text-green-500" />
                  </div>
                  <span className="text-xs font-semibold text-foreground">API Spec</span>
                </div>
                <div className="text-[10px] font-mono text-foreground-secondary space-y-1">
                  <p>POST /auth/register</p>
                  <p>GET /projects/:id</p>
                  <p>PUT /docs/:id/export</p>
                </div>
              </Card>
            </motion.div>

            {/* Card 3 — Bottom Center-Left */}
            <motion.div
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, delay: 0.9, ease: "easeOut" }}
              className="absolute top-16 left-[15%] w-56"
            >
              <Card className="glass backdrop-blur-xl">
                <div className="flex items-center gap-2 mb-2">
                  <Database className="h-4 w-4 text-purple-500" />
                  <span className="text-xs font-semibold text-foreground">ERD Preview</span>
                </div>
                <div className="text-[10px] font-mono text-foreground-secondary">
                  <p>User (1)──(N) Project</p>
                  <p>Project (1)──(N) Doc</p>
                </div>
              </Card>
            </motion.div>

            {/* Card 4 — Bottom Center-Right */}
            <motion.div
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, delay: 1.1, ease: "easeOut" }}
              className="absolute top-20 right-[12%] w-52"
            >
              <Card className="glass backdrop-blur-xl">
                <div className="flex items-center gap-2">
                  <Clock className="h-4 w-4 text-orange-500" />
                  <span className="text-xs font-semibold text-foreground">Generation Time</span>
                </div>
                <p className="text-2xl font-bold font-display gradient-text mt-1">4m 12s</p>
                <p className="text-[10px] text-foreground-secondary">Full SRS + API + ERD</p>
              </Card>
            </motion.div>
          </div>
        </motion.div>

        {/* Scroll indicator */}
        <motion.div
          animate={{ y: [0, 8, 0] }}
          transition={{ duration: 2, repeat: Infinity }}
          className="absolute bottom-8 left-1/2 -translate-x-1/2"
        >
          <div className="w-6 h-10 rounded-full border-2 border-border flex items-start justify-center pt-2">
            <motion.div
              animate={{ y: [0, 12, 0], opacity: [1, 0, 1] }}
              transition={{ duration: 2, repeat: Infinity }}
              className="w-1.5 h-1.5 rounded-full bg-primary"
            />
          </div>
        </motion.div>
      </section>

      {/* ════════════════════════════════════════════════════════════
          SECTION 2 — INTERACTIVE DEMO (#demo)
          ════════════════════════════════════════════════════════════ */}
      <section id="demo" className="py-28 relative overflow-hidden" ref={demoRef}>
        <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 relative">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-5xl font-bold font-display">
                <span className="gradient-text">See Velocira in Action</span>
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                Watch how a simple project idea transforms into production-ready documentation in under 5 minutes.
              </p>
              {!demoPlaying && (
                <Button
                  variant="secondary"
                  size="lg"
                  icon={<Play className="h-5 w-5" />}
                  className="mt-6"
                  onClick={startDemo}
                >
                  Play Demo
                </Button>
              )}
            </div>
          </FadeIn>

          {/* Demo Terminal */}
          <FadeIn delay={0.2}>
            <div
              className="relative mx-auto max-w-4xl"
              style={{ perspective: "1200px" }}
            >
              <motion.div
                initial={{ rotateX: 8 }}
                whileInView={{ rotateX: 2 }}
                transition={{ duration: 1.2, ease: "easeOut" }}
                style={{ transformStyle: "preserve-3d", willChange: "transform" }}
                className="rounded-2xl border border-border bg-card shadow-2xl overflow-hidden"
              >
                {/* Terminal Header */}
                <div className="flex items-center gap-2 px-4 py-3 border-b border-border bg-background-secondary/50">
                  <div className="w-3 h-3 rounded-full bg-red-500/80" />
                  <div className="w-3 h-3 rounded-full bg-yellow-500/80" />
                  <div className="w-3 h-3 rounded-full bg-green-500/80" />
                  <span className="ms-3 text-xs text-foreground-secondary font-mono">velocira — interactive demo</span>
                  <div className="ms-auto flex items-center gap-2">
                    <span className="text-[10px] text-foreground-secondary">Step {Math.min(demoStep + 1, 4)}/4</span>
                    <div className="flex gap-1">
                      {[0, 1, 2, 3].map((s) => (
                        <div
                          key={s}
                          className={`w-2 h-2 rounded-full transition-colors ${
                            demoStep >= s ? "bg-primary" : "bg-border"
                          }`}
                        />
                      ))}
                    </div>
                  </div>
                </div>

                {/* Demo Content */}
                <div className="p-6 min-h-[400px] relative">
                  <AnimatePresence mode="wait">
                    {/* Step 0: Typing */}
                    {demoStep === 0 && (
                      <motion.div
                        key="step0"
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -20 }}
                        className="space-y-4"
                      >
                        <div className="flex items-center gap-2 mb-4">
                          <Terminal className="h-5 w-5 text-primary" />
                          <span className="text-sm font-semibold text-primary">Step 1: Describe Your Project</span>
                        </div>
                        <div className="rounded-xl border border-border bg-background-secondary/50 p-4">
                          <p className="text-sm text-foreground font-mono leading-relaxed">
                            {typedText}
                            <motion.span
                              animate={{ opacity: [1, 0] }}
                              transition={{ duration: 0.5, repeat: Infinity }}
                              className="inline-block w-2 h-4 bg-primary ms-0.5 align-middle"
                            />
                          </p>
                        </div>
                        <p className="text-xs text-foreground-secondary">
                          Velocira AI is analyzing your project description...
                        </p>
                      </motion.div>
                    )}

                    {/* Step 1: Select Project Type */}
                    {demoStep === 1 && (
                      <motion.div
                        key="step1"
                        initial={{ opacity: 0, rotateY: -15 }}
                        animate={{ opacity: 1, rotateY: 0 }}
                        exit={{ opacity: 0, rotateY: 15 }}
                        transition={{ duration: 0.5 }}
                        style={{ transformStyle: "preserve-3d" }}
                        className="space-y-4"
                      >
                        <div className="flex items-center gap-2 mb-4">
                          <Layers className="h-5 w-5 text-primary" />
                          <span className="text-sm font-semibold text-primary">Step 2: Select Project Type</span>
                        </div>
                        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                          {["Web App", "Mobile App", "REST API", "ML Pipeline", "Microservices", "Desktop App"].map(
                            (type, i) => (
                              <motion.div
                                key={type}
                                initial={{ opacity: 0, rotateY: -90, scale: 0.8 }}
                                animate={{ opacity: 1, rotateY: 0, scale: 1 }}
                                transition={{ delay: i * 0.1, duration: 0.4 }}
                                style={{ transformStyle: "preserve-3d" }}
                                className={`p-3 rounded-xl border text-center text-sm font-medium cursor-pointer transition-all ${
                                  i === 0
                                    ? "border-primary bg-primary/10 text-primary"
                                    : "border-border bg-background-secondary/30 text-foreground-secondary hover:border-border"
                                }`}
                              >
                                {type}
                              </motion.div>
                            )
                          )}
                        </div>
                      </motion.div>
                    )}

                    {/* Step 2: AI Generating */}
                    {demoStep === 2 && (
                      <motion.div
                        key="step2"
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -20 }}
                        className="space-y-6"
                      >
                        <div className="flex items-center gap-2 mb-4">
                          <Cpu className="h-5 w-5 text-primary" />
                          <span className="text-sm font-semibold text-primary">Step 3: AI Generating Documentation</span>
                        </div>
                        <div className="space-y-3">
                          {["Analyzing requirements...", "Generating SRS document...", "Creating ERD schema...", "Building API specification...", "Compiling architecture docs..."].map(
                            (task, i) => (
                              <motion.div
                                key={task}
                                initial={{ opacity: 0, x: -20 }}
                                animate={{ opacity: progress > i * 20 ? 1 : 0.3, x: 0 }}
                                transition={{ delay: i * 0.15 }}
                                className="flex items-center gap-3"
                              >
                                {progress > (i + 1) * 20 ? (
                                  <CheckCircle2 className="h-4 w-4 text-success shrink-0" />
                                ) : (
                                  <motion.div
                                    animate={{ rotate: 360 }}
                                    transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
                                    className="h-4 w-4 border-2 border-primary border-t-transparent rounded-full shrink-0"
                                  />
                                )}
                                <span className="text-sm text-foreground-secondary">{task}</span>
                              </motion.div>
                            )
                          )}
                        </div>
                        <div className="mt-4">
                          <div className="flex justify-between text-xs text-foreground-secondary mb-1">
                            <span>Progress</span>
                            <span>{progress}%</span>
                          </div>
                          <div className="h-2 rounded-full bg-border overflow-hidden">
                            <motion.div
                              className="h-full rounded-full bg-gradient-to-r from-primary to-accent"
                              style={{ width: `${progress}%`, willChange: "width" }}
                            />
                          </div>
                        </div>
                      </motion.div>
                    )}

                    {/* Step 3: Document Preview */}
                    {demoStep === 3 && (
                      <motion.div
                        key="step3"
                        initial={{ opacity: 0, rotateX: 15, y: 40 }}
                        animate={{ opacity: 1, rotateX: 0, y: 0 }}
                        transition={{ duration: 0.7, ease: "easeOut" }}
                        style={{ perspective: 800, transformStyle: "preserve-3d" }}
                        className="space-y-4"
                      >
                        <div className="flex items-center justify-between mb-4">
                          <div className="flex items-center gap-2">
                            <CheckCircle2 className="h-5 w-5 text-success" />
                            <span className="text-sm font-semibold text-success">Step 4: Documentation Ready!</span>
                          </div>
                          <span className="text-xs text-foreground-secondary">Generated in 4m 12s</span>
                        </div>
                        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                          {["SRS Document", "Use Cases", "ERD Schema", "API Spec", "Architecture", "Roadmap"].map(
                            (doc, i) => (
                              <motion.div
                                key={doc}
                                initial={{ opacity: 0, scale: 0.8, rotateY: -30 }}
                                animate={{ opacity: 1, scale: 1, rotateY: 0 }}
                                transition={{ delay: i * 0.08, duration: 0.4 }}
                                style={{ transformStyle: "preserve-3d" }}
                                className="p-3 rounded-xl border border-primary/20 bg-primary/5 text-center"
                              >
                                <FileText className="h-5 w-5 text-primary mx-auto mb-1" />
                                <p className="text-xs font-medium text-foreground">{doc}</p>
                                <p className="text-[10px] text-success mt-1">✓ Ready</p>
                              </motion.div>
                            )
                          )}
                        </div>
                        <div className="flex gap-3 mt-4">
                          <Button size="sm" icon={<ArrowRight className="h-4 w-4" />}>Export All</Button>
                          <Button size="sm" variant="outline">Preview</Button>
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              </motion.div>
            </div>
          </FadeIn>
        </div>
      </section>

      {/* ════════════════════════════════════════════════════════════
          SECTION 3 — FEATURES (6 cards with 3D tilt)
          ════════════════════════════════════════════════════════════ */}
      <section className="py-28 relative">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-5xl font-bold font-display">
                <span className="gradient-text">{t("landing.features.title")}</span>
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                {t("landing.features.subtitle")}
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {featureData.map(({ icon: Icon, key, color }) => (
              <StaggerItem key={key}>
                <TiltCard className="h-full">
                  <Card hover className="h-full group relative overflow-hidden">
                    <div className={`absolute inset-0 bg-gradient-to-br ${color} opacity-0 group-hover:opacity-100 transition-opacity duration-500`} />
                    <div className="relative">
                      <div className="h-14 w-14 rounded-2xl bg-primary/10 flex items-center justify-center mb-5 transition-all duration-300">
                        <Icon className="h-7 w-7 text-primary" />
                      </div>
                      <h3 className="text-lg font-semibold text-foreground mb-2 font-display">
                        {t(`landing.features.${key}.title`)}
                      </h3>
                      <p className="text-sm text-foreground-secondary leading-relaxed">
                        {t(`landing.features.${key}.description`)}
                      </p>
                    </div>
                  </Card>
                </TiltCard>
              </StaggerItem>
            ))}
          </StaggerContainer>
        </div>
      </section>

      {/* ════════════════════════════════════════════════════════════
          SECTION 4 — HOW IT WORKS (4 steps with parallax)
          ════════════════════════════════════════════════════════════ */}
      <section className="py-28 bg-card/30 relative overflow-hidden" ref={howRef}>
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative">
          <FadeIn>
            <div className="text-center mb-20">
              <h2 className="text-3xl sm:text-5xl font-bold font-display">
                <span className="gradient-text">How It Works</span>
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                Four simple steps from idea to complete, production-ready documentation.
              </p>
            </div>
          </FadeIn>

          <motion.div style={{ y: howY }} className="relative">
            {/* Connecting line */}
            <div className="hidden lg:block absolute top-1/2 left-0 right-0 h-0.5 bg-gradient-to-r from-transparent via-primary/30 to-transparent -translate-y-1/2" />

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-8">
              {howItWorks.map((step, i) => (
                <FadeIn key={step.title} delay={i * 0.15}>
                  <TiltCard className="h-full">
                    <Card hover className="h-full text-center relative group">
                      {/* Step number */}
                      <div className="absolute -top-4 left-1/2 -translate-x-1/2 w-8 h-8 rounded-full bg-primary text-on-primary flex items-center justify-center text-sm font-bold shadow-lg z-10">
                        {i + 1}
                      </div>
                      <div className="pt-4">
                        <div className="h-14 w-14 rounded-2xl bg-primary/10 flex items-center justify-center mx-auto mb-4 group-hover:bg-primary/20 transition-colors">
                          <step.icon className="h-7 w-7 text-primary" />
                        </div>
                        <h3 className="text-base font-semibold text-foreground mb-2 font-display">
                          {step.title}
                        </h3>
                        <p className="text-sm text-foreground-secondary leading-relaxed">
                          {step.desc}
                        </p>
                      </div>
                      {i < 3 && (
                        <ChevronRight className="hidden lg:block absolute -right-5 top-1/2 -translate-y-1/2 h-5 w-5 text-primary/50" />
                      )}
                    </Card>
                  </TiltCard>
                </FadeIn>
              ))}
            </div>
          </motion.div>
        </div>
      </section>

      {/* ════════════════════════════════════════════════════════════
          SECTION 5 — DOCUMENT TYPES SHOWCASE (Tabbed 3D Preview)
          ════════════════════════════════════════════════════════════ */}
      <section className="py-28 relative">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-5xl font-bold font-display">
                <span className="gradient-text">Document Types</span>
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                Generate every document your project needs — from specifications to deployment guides.
              </p>
            </div>
          </FadeIn>

          <FadeIn delay={0.2}>
            {/* Tabs */}
            <div className="flex flex-wrap justify-center gap-2 mb-10">
              {docTypes.map((doc, i) => (
                <button
                  key={doc.label}
                  onClick={() => setActiveDocTab(i)}
                  className={`px-4 py-2 rounded-xl text-sm font-medium transition-all cursor-pointer ${
                    activeDocTab === i
                      ? "bg-primary text-on-primary shadow-lg"
                      : "bg-card border border-border text-foreground-secondary hover:border-border"
                  }`}
                >
                  {doc.label}
                </button>
              ))}
            </div>

            {/* 3D Document Preview */}
            <div className="relative mx-auto max-w-3xl" style={{ perspective: "1200px" }}>
              <AnimatePresence mode="wait">
                <motion.div
                  key={activeDocTab}
                  initial={{ opacity: 0, rotateY: -12, rotateX: 5, scale: 0.95 }}
                  animate={{ opacity: 1, rotateY: 0, rotateX: 2, scale: 1 }}
                  exit={{ opacity: 0, rotateY: 12, rotateX: -5, scale: 0.95 }}
                  transition={{ duration: 0.5, ease: "easeOut" }}
                  style={{ transformStyle: "preserve-3d", willChange: "transform" }}
                  className="rounded-2xl border border-border bg-card shadow-2xl overflow-hidden"
                >
                  {/* Doc Header */}
                  <div className="px-6 py-4 border-b border-border bg-background-secondary/30">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-xs uppercase tracking-[0.15em] text-primary font-semibold">
                          {docTypes[activeDocTab].label}
                        </p>
                        <h3 className="text-lg font-semibold text-foreground font-display">
                          {docTypes[activeDocTab].title}
                        </h3>
                      </div>
                      <span className="inline-flex items-center rounded-full border border-success/35 bg-success/10 px-3 py-1 text-[10px] font-semibold uppercase tracking-wider text-success">
                        Generated
                      </span>
                    </div>
                  </div>
                  {/* Doc Content */}
                  <div className="p-6">
                    <pre className="text-sm font-mono text-foreground-secondary whitespace-pre-wrap leading-relaxed">
                      {docTypes[activeDocTab].content}
                    </pre>
                  </div>
                </motion.div>
              </AnimatePresence>
            </div>
          </FadeIn>
        </div>
      </section>

      {/* ════════════════════════════════════════════════════════════
          SECTION 6 — STATS (Animated Counters)
          ════════════════════════════════════════════════════════════ */}
      <section className="py-24 bg-card/30 relative" ref={statsRef}>
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-5xl font-bold font-display">
                <span className="gradient-text">Trusted by Thousands</span>
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary">
                Numbers that speak for themselves.
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="grid grid-cols-2 lg:grid-cols-4 gap-6">
            {[
              { value: statDocs, suffix: "+", label: t("landing.stats.docs"), icon: FileText, display: "50K" },
              { value: statUsers, suffix: "+", label: t("landing.stats.users"), icon: Users, display: "10K" },
              { value: statTime, suffix: " min", label: t("landing.stats.time"), icon: Rocket, prefix: "<" },
              { value: statSatisfaction, suffix: "%", label: t("landing.stats.satisfaction"), icon: Sparkles },
            ].map((stat) => (
              <StaggerItem key={stat.label}>
                <TiltCard>
                  <Card className="text-center py-8" hover>
                    <stat.icon className="h-10 w-10 text-primary mx-auto mb-4" />
                    <p className="text-4xl font-bold font-display gradient-text">
                      {stat.prefix || ""}
                      {stat.value >= 1000
                        ? `${Math.floor(stat.value / 1000)}K`
                        : stat.value}
                      {stat.suffix}
                    </p>
                    <p className="text-sm text-foreground-secondary mt-2">{stat.label}</p>
                  </Card>
                </TiltCard>
              </StaggerItem>
            ))}
          </StaggerContainer>
        </div>
      </section>

      {/* ════════════════════════════════════════════════════════════
          SECTION 7 — TESTIMONIALS
          ════════════════════════════════════════════════════════════ */}
      <section className="py-28 relative">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-5xl font-bold font-display">
                <span className="gradient-text">Loved by Developers</span>
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                See what our users have to say about transforming their documentation workflow.
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {testimonials.map((testimonial) => (
              <StaggerItem key={testimonial.name}>
                <Card hover className="h-full flex flex-col">
                    <div className="flex items-center gap-1 mb-4">
                      {[...Array(5)].map((_, s) => (
                        <Star key={s} className="h-4 w-4 fill-yellow-500 text-yellow-500" />
                      ))}
                    </div>
                    <p className="text-sm text-foreground-secondary leading-relaxed italic mb-6">
                      &ldquo;{testimonial.quote}&rdquo;
                    </p>
                    <div className="flex items-center gap-3 mt-auto">
                      <div className="w-10 h-10 rounded-full bg-primary/10 flex items-center justify-center text-sm font-bold text-primary">
                        {testimonial.avatar}
                      </div>
                      <div>
                        <p className="text-sm font-semibold text-foreground">{testimonial.name}</p>
                        <p className="text-xs text-foreground-secondary">{testimonial.role}</p>
                      </div>
                    </div>
                  </Card>
              </StaggerItem>
            ))}
          </StaggerContainer>
        </div>
      </section>

      {/* ════════════════════════════════════════════════════════════
          SECTION 8 — PRICING PREVIEW
          ════════════════════════════════════════════════════════════ */}
      <section className="py-28 bg-card/30 relative overflow-hidden">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-5xl font-bold font-display">
                <span className="gradient-text">Simple, Transparent Pricing</span>
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                Start free. Scale as you grow. No hidden fees, no surprises.
              </p>
            </div>
          </FadeIn>

          <StaggerContainer className="grid grid-cols-1 md:grid-cols-3 gap-6 max-w-5xl mx-auto items-stretch">
            {pricingTiers.map((tier) => (
              <StaggerItem key={tier.name} className="flex">
                <TiltCard className="flex w-full">
                  <Card
                    hover
                    glow={tier.highlight}
                    className={`flex flex-col w-full relative ${
                      tier.highlight ? "border-primary/50" : ""
                    }`}
                  >
                    {tier.highlight && (
                      <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-4 py-1 rounded-full bg-primary text-on-primary text-xs font-bold">
                        Most Popular
                      </div>
                    )}
                    <div className="text-center pt-4">
                      <h3 className="text-xl font-semibold text-foreground font-display">{tier.name}</h3>
                      <div className="mt-4 mb-6">
                        <span className="text-4xl font-bold font-display gradient-text">{tier.price}</span>
                        <span className="text-foreground-secondary text-sm">{tier.period}</span>
                      </div>
                    </div>
                    <ul className="space-y-3 flex-1">
                      {tier.features.map((feature) => (
                        <li key={feature} className="flex items-center gap-2 text-sm text-foreground-secondary">
                          <CheckCircle2 className="h-4 w-4 text-success shrink-0" />
                          {feature}
                        </li>
                      ))}
                    </ul>
                    <div className="mt-8">
                      <Link href="/register" className="block">
                        <Button
                          variant={tier.highlight ? "primary" : "outline"}
                          className="w-full"
                        >
                          {tier.name === "Enterprise" ? "Contact Sales" : "Get Started"}
                        </Button>
                      </Link>
                    </div>
                  </Card>
                </TiltCard>
              </StaggerItem>
            ))}
          </StaggerContainer>

          <FadeIn delay={0.4}>
            <p className="text-center mt-8 text-sm text-foreground-secondary">
              All plans include SSL encryption, daily backups, and 99.9% uptime guarantee.{" "}
              <Link href="/pricing" className="text-primary hover:underline">
                See full pricing details →
              </Link>
            </p>
          </FadeIn>
        </div>
      </section>

      {/* ════════════════════════════════════════════════════════════
          SECTION 9 — TECH STACK
          ════════════════════════════════════════════════════════════ */}
      <section className="py-28 relative">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <FadeIn>
            <div className="text-center mb-16">
              <h2 className="text-3xl sm:text-5xl font-bold font-display">
                <span className="gradient-text">Built with Modern Tech</span>
              </h2>
              <p className="mt-4 text-lg text-foreground-secondary max-w-2xl mx-auto">
                Enterprise-grade stack powering your documentation pipeline.
              </p>
            </div>
          </FadeIn>

          <div className="flex flex-wrap justify-center gap-4">
            {techStack.map((tech, i) => (
              <motion.div
                key={tech}
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ delay: i * 0.05, duration: 0.4 }}
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full border border-border bg-card hover:border-primary/30 hover:bg-primary/5 transition-all duration-200 text-sm font-medium text-foreground-secondary hover:text-primary hover:-translate-y-0.5 cursor-default"
              >
                <Code2 className="h-4 w-4" />
                {tech}
              </motion.div>
            ))}
          </div>

          <FadeIn delay={0.3}>
            <div className="mt-16 grid grid-cols-2 sm:grid-cols-4 gap-6 max-w-3xl mx-auto">
              {[
                { icon: Server, label: "Microservices", desc: "Scalable architecture" },
                { icon: Lock, label: "SOC2 Ready", desc: "Enterprise security" },
                { icon: Cloud, label: "Cloud Native", desc: "Deploy anywhere" },
                { icon: Monitor, label: "99.9% Uptime", desc: "Always available" },
              ].map((item) => (
                <div key={item.label} className="text-center">
                  <div className="h-12 w-12 rounded-xl bg-primary/10 flex items-center justify-center mx-auto mb-3">
                    <item.icon className="h-6 w-6 text-primary" />
                  </div>
                  <p className="text-sm font-semibold text-foreground">{item.label}</p>
                  <p className="text-xs text-foreground-secondary mt-0.5">{item.desc}</p>
                </div>
              ))}
            </div>
          </FadeIn>
        </div>
      </section>

      {/* ════════════════════════════════════════════════════════════
          SECTION 10 — FINAL CTA
          ════════════════════════════════════════════════════════════ */}
      <section className="py-32 relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-b from-transparent via-primary/[0.02] to-transparent" />
        {/* Geometric accents */}
        <div className="absolute top-10 left-[10%] w-24 h-24 border border-primary/[0.06] rotate-45 pointer-events-none hidden lg:block" />
        <div className="absolute bottom-10 right-[10%] w-16 h-16 border border-primary/[0.05] rotate-45 pointer-events-none hidden lg:block" />

        <div className="relative max-w-4xl mx-auto px-4 text-center">
          <FadeIn>
            <h2 className="text-4xl sm:text-6xl font-bold font-display mb-6 leading-tight">
              <span className="gradient-text">{t("landing.cta2.title")}</span>
            </h2>
            <p className="text-lg sm:text-xl text-foreground-secondary mb-10 max-w-2xl mx-auto leading-relaxed">
              {t("landing.cta2.subtitle")}
            </p>
            <Link href="/register">
              <Button size="lg" icon={<Zap className="h-5 w-5" />}>
                {t("landing.cta2.button")}
              </Button>
            </Link>
          </FadeIn>

          {/* Mini stats under CTA */}
          <FadeIn delay={0.3}>
            <div className="mt-16 flex flex-wrap justify-center gap-8 text-sm text-foreground-secondary">
              <div className="flex items-center gap-2">
                <Shield className="h-4 w-4 text-primary" />
                <span>Enterprise-grade security</span>
              </div>
              <div className="flex items-center gap-2">
                <Rocket className="h-4 w-4 text-primary" />
                <span>Used by 10,000+ developers</span>
              </div>
              <div className="flex items-center gap-2">
                <Zap className="h-4 w-4 text-primary" />
                <span>Generate in under 5 minutes</span>
              </div>
            </div>
          </FadeIn>

          <FadeIn delay={0.5}>
            <div className="mt-12 flex items-center justify-center gap-1">
              {[...Array(5)].map((_, i) => (
                <Star key={i} className="h-5 w-5 fill-yellow-500 text-yellow-500" />
              ))}
              <span className="ms-2 text-sm text-foreground-secondary">
                Rated 4.9/5 by 2,000+ developers
              </span>
            </div>
          </FadeIn>
        </div>
      </section>
    </PageTransition>
  );
}
