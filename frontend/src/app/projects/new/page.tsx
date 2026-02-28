"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Globe,
  Smartphone,
  Brain,
  Cpu,
  Monitor,
  Server,
  ArrowLeft,
  ArrowRight,
  Check,
  Sparkles,
  FileText,
  Users,
  User,
  Layers,
  Plus,
  X,
  Loader2,
  Rocket,
  ChevronRight,
} from "lucide-react";
import { useLocale } from "@/providers/LocaleProvider";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import Card from "@/components/ui/Card";
import { FadeIn, PageTransition } from "@/components/ui/Animations";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";

/* ------------------------------------------------------------------ */
/*  Constants & Types                                                  */
/* ------------------------------------------------------------------ */

const STEPS = ["Details", "Type", "Tech Stack", "Review"] as const;
const TOTAL_STEPS = STEPS.length;
const DESC_MIN = 50;
const DESC_MAX = 2000;

interface ProjectForm {
  name: string;
  description: string;
  teamSize: string;
  ownerName: string;
  type: string;
  techStack: string[];
  customTech: string;
}

const PROJECT_TYPES = [
  { id: "web-app", label: "Web App", icon: Globe, color: "text-primary" },
  { id: "mobile-app", label: "Mobile App", icon: Smartphone, color: "text-accent" },
  { id: "ai-system", label: "AI System", icon: Brain, color: "text-info" },
  { id: "iot", label: "IoT", icon: Cpu, color: "text-success" },
  { id: "desktop-app", label: "Desktop App", icon: Monitor, color: "text-warning" },
  { id: "api-backend", label: "API / Backend Service", icon: Server, color: "text-error" },
] as const;

const TECH_STACK: Record<string, string[]> = {
  Frontend: ["React", "Next.js", "Vue", "Angular", "Svelte", "Flutter"],
  Backend: ["Spring Boot", "Express.js", "Django", "FastAPI", "Laravel", ".NET"],
  Database: ["PostgreSQL", "MySQL", "MongoDB", "Redis", "Firebase", "Supabase"],
  Cloud: ["AWS", "GCP", "Azure", "DigitalOcean", "Vercel", "Railway"],
};

const slideVariants = {
  enter: (direction: number) => ({
    x: direction > 0 ? 300 : -300,
    opacity: 0,
  }),
  center: { x: 0, opacity: 1 },
  exit: (direction: number) => ({
    x: direction > 0 ? -300 : 300,
    opacity: 0,
  }),
};

/* ------------------------------------------------------------------ */
/*  Step Indicator                                                     */
/* ------------------------------------------------------------------ */

function StepIndicator({ current }: { current: number }) {
  return (
    <div className="flex items-center justify-center gap-0 mb-10">
      {STEPS.map((label, idx) => {
        const isCompleted = idx < current;
        const isCurrent = idx === current;
        return (
          <div key={label} className="flex items-center">
            {/* Step circle */}
            <div className="flex flex-col items-center">
              <motion.div
                className={`
                  relative z-10 flex h-10 w-10 items-center justify-center rounded-full
                  border-2 text-sm font-semibold transition-all duration-300
                  ${
                    isCompleted
                      ? "border-primary bg-primary text-on-primary"
                      : isCurrent
                      ? "border-primary bg-primary/10 text-primary"
                      : "border-border bg-background-secondary text-foreground-secondary"
                  }
                `}
                animate={isCurrent ? { scale: [1, 1.08, 1] } : {}}
                transition={{ duration: 2, repeat: Infinity, ease: "easeInOut" }}
              >
                {isCompleted ? <Check className="h-4 w-4" /> : idx + 1}
              </motion.div>
              <span
                className={`mt-2 text-xs font-medium whitespace-nowrap ${
                  isCurrent ? "text-primary" : "text-foreground-secondary"
                }`}
              >
                {label}
              </span>
            </div>
            {/* Connector line */}
            {idx < TOTAL_STEPS - 1 && (
              <div
                className={`h-0.5 w-12 sm:w-20 mx-2 mt-[-1.25rem] transition-colors duration-300 ${
                  idx < current ? "bg-primary" : "bg-border"
                }`}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Page Component                                                     */
/* ------------------------------------------------------------------ */

export default function NewProjectPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading } = useAuthStore();
  const { t } = useLocale();

  const [step, setStep] = useState(0);
  const [direction, setDirection] = useState(1);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [isGenerating, setIsGenerating] = useState(false);
  const [generationProgress, setGenerationProgress] = useState(0);
  const genIntervalRef = useRef<ReturnType<typeof setInterval>>(undefined);

  const [form, setForm] = useState<ProjectForm>({
    name: "",
    description: "",
    teamSize: "",
    ownerName: "",
    type: "",
    techStack: [],
    customTech: "",
  });

  /* ----- Auth guard ----- */
  useEffect(() => {
    if (!authLoading && !isAuthenticated) {
      router.replace("/login");
    }
  }, [authLoading, isAuthenticated, router]);

  /* ----- Form helpers ----- */
  const updateField = useCallback(
    <K extends keyof ProjectForm>(key: K, value: ProjectForm[K]) => {
      setForm((prev) => ({ ...prev, [key]: value }));
      setErrors((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
    },
    []
  );

  const toggleTech = useCallback((tech: string) => {
    setForm((prev) => ({
      ...prev,
      techStack: prev.techStack.includes(tech)
        ? prev.techStack.filter((t) => t !== tech)
        : [...prev.techStack, tech],
    }));
  }, []);

  const addCustomTech = useCallback(() => {
    const trimmed = form.customTech.trim();
    if (trimmed && !form.techStack.includes(trimmed)) {
      setForm((prev) => ({
        ...prev,
        techStack: [...prev.techStack, trimmed],
        customTech: "",
      }));
    }
  }, [form.customTech, form.techStack]);

  /* ----- Validation ----- */
  const validateStep = useCallback((): boolean => {
    const newErrors: Record<string, string> = {};

    if (step === 0) {
      if (!form.name.trim()) newErrors.name = "Project name is required";
      if (form.description.length < DESC_MIN)
        newErrors.description = `Description must be at least ${DESC_MIN} characters`;
      if (form.description.length > DESC_MAX)
        newErrors.description = `Description must be at most ${DESC_MAX} characters`;
    }

    if (step === 1) {
      if (!form.type) newErrors.type = "Please select a project type";
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [step, form.name, form.description, form.type]);

  /* ----- Navigation ----- */
  const goNext = useCallback(() => {
    if (!validateStep()) return;
    setDirection(1);
    setStep((s) => Math.min(s + 1, TOTAL_STEPS - 1));
  }, [validateStep]);

  const goBack = useCallback(() => {
    setDirection(-1);
    setStep((s) => Math.max(s - 1, 0));
  }, []);

  /* ----- Generation ----- */
  const handleGenerate = useCallback(() => {
    if (!validateStep()) return;
    setIsGenerating(true);
    setGenerationProgress(0);

    const stages = [12, 28, 45, 62, 78, 91, 100];
    let idx = 0;

    if (genIntervalRef.current) clearInterval(genIntervalRef.current);
    genIntervalRef.current = setInterval(() => {
      if (idx < stages.length) {
        setGenerationProgress(stages[idx]);
        idx++;
      } else {
        clearInterval(genIntervalRef.current);
        const mockId = Math.random().toString(36).substring(2, 10);
        router.push(`/projects/${mockId}`);
      }
    }, 450);
  }, [validateStep, router]);

  useEffect(() => {
    return () => {
      if (genIntervalRef.current) clearInterval(genIntervalRef.current);
    };
  }, []);

  /* ----- Loading / unauthenticated guard ----- */
  if (authLoading || !isAuthenticated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background-secondary">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  /* ================================================================ */
  /*  Step Renderers                                                   */
  /* ================================================================ */

  const renderStepDetails = () => (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-display font-bold text-foreground mb-1">
          Project Details
        </h2>
        <p className="text-foreground-secondary text-sm">
          Tell us about your project idea. The more detail you provide, the
          better the generated documentation will be.
        </p>
      </div>

      {/* Name */}
      <Input
        label="Project Name"
        placeholder="e.g. Velocira, ShopFlow, HealthTrack…"
        icon={<FileText className="h-4 w-4" />}
        value={form.name}
        onChange={(e) => updateField("name", e.target.value)}
        required
        autoComplete="off"
      />
      {errors.name && <p className="text-sm text-error -mt-4">{errors.name}</p>}

      {/* Description */}
      <div className="w-full space-y-1.5">
        <label className="block text-sm font-medium text-foreground-secondary">
          Project Description / Idea <span className="text-error">*</span>
        </label>
        <textarea
          rows={5}
          placeholder="Describe your project idea in detail — what it does, who it's for, key features…"
          value={form.description}
          onChange={(e) => updateField("description", e.target.value)}
          className={`
            w-full rounded-xl border bg-input-bg px-4 py-3 text-foreground
            placeholder:text-placeholder transition-all duration-200 resize-y
            focus:outline-none focus:border-input-focus focus:ring-2 focus:ring-primary/20
            ${errors.description ? "border-error focus:border-error focus:ring-error/20" : "border-input-border"}
          `}
        />
        <div className="flex items-center justify-between">
          {errors.description ? (
            <p className="text-sm text-error">{errors.description}</p>
          ) : (
            <span />
          )}
          <span
            className={`text-xs tabular-nums ${
              form.description.length > DESC_MAX
                ? "text-error"
                : form.description.length >= DESC_MIN
                ? "text-success"
                : "text-foreground-secondary"
            }`}
          >
            {form.description.length} / {DESC_MAX}
          </span>
        </div>
      </div>

      {/* Team size + Owner */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Input
          label="Team Size"
          type="number"
          placeholder="e.g. 5"
          icon={<Users className="h-4 w-4" />}
          value={form.teamSize}
          onChange={(e) => updateField("teamSize", e.target.value)}
          autoComplete="off"
        />
        <Input
          label="Project Owner"
          placeholder="e.g. John Doe"
          icon={<User className="h-4 w-4" />}
          value={form.ownerName}
          onChange={(e) => updateField("ownerName", e.target.value)}
          autoComplete="off"
        />
      </div>
    </div>
  );

  const renderStepType = () => (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-display font-bold text-foreground mb-1">
          Project Type
        </h2>
        <p className="text-foreground-secondary text-sm">
          Select the type that best describes your project.
        </p>
      </div>
      {errors.type && <p className="text-sm text-error">{errors.type}</p>}

      <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
        {PROJECT_TYPES.map(({ id, label, icon: Icon, color }) => {
          const selected = form.type === id;
          return (
            <motion.button
              key={id}
              type="button"
              whileHover={{ scale: 1.03 }}
              whileTap={{ scale: 0.97 }}
              onClick={() => updateField("type", id)}
              className={`
                relative flex flex-col items-center gap-3 rounded-2xl border-2 p-6
                transition-all duration-200 cursor-pointer text-center
                ${
                  selected
                    ? "border-primary bg-primary/10 shadow-md shadow-primary/10"
                    : "border-border bg-card hover:border-border-hover hover:bg-card-hover"
                }
              `}
            >
              {selected && (
                <motion.div
                  layoutId="type-check"
                  className="absolute top-2 end-2 flex h-5 w-5 items-center justify-center rounded-full bg-primary text-on-primary"
                >
                  <Check className="h-3 w-3" />
                </motion.div>
              )}
              <div
                className={`flex h-12 w-12 items-center justify-center rounded-xl ${
                  selected ? "bg-primary/20" : "bg-background-secondary"
                }`}
              >
                <Icon className={`h-6 w-6 ${selected ? "text-primary" : color}`} />
              </div>
              <span
                className={`text-sm font-semibold ${
                  selected ? "text-primary" : "text-foreground"
                }`}
              >
                {label}
              </span>
            </motion.button>
          );
        })}
      </div>
    </div>
  );

  const renderStepTechStack = () => (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-display font-bold text-foreground mb-1">
          Tech Stack
        </h2>
        <p className="text-foreground-secondary text-sm">
          Optionally select the technologies your project will use. This helps
          generate more relevant documentation.
        </p>
      </div>

      {Object.entries(TECH_STACK).map(([category, items]) => (
        <div key={category}>
          <h3 className="text-sm font-semibold text-foreground-secondary mb-3 uppercase tracking-wider">
            {category}
          </h3>
          <div className="flex flex-wrap gap-2">
            {items.map((tech) => {
              const selected = form.techStack.includes(tech);
              return (
                <motion.button
                  key={tech}
                  type="button"
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                  onClick={() => toggleTech(tech)}
                  className={`
                    inline-flex items-center gap-1.5 rounded-full px-4 py-2 text-sm
                    font-medium border transition-all duration-200 cursor-pointer
                    ${
                      selected
                        ? "bg-primary text-on-primary border-primary shadow-sm shadow-primary/20"
                        : "bg-card border-border text-foreground hover:border-border-hover hover:bg-card-hover"
                    }
                  `}
                >
                  {selected && <Check className="h-3 w-3" />}
                  {tech}
                </motion.button>
              );
            })}
          </div>
        </div>
      ))}

      {/* Custom tech input */}
      <div>
        <h3 className="text-sm font-semibold text-foreground-secondary mb-3 uppercase tracking-wider">
          Custom
        </h3>
        <div className="flex gap-2">
          <div className="flex-1">
            <Input
              placeholder="Add custom technology…"
              icon={<Plus className="h-4 w-4" />}
              value={form.customTech}
              onChange={(e) => updateField("customTech", e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  addCustomTech();
                }
              }}
              autoComplete="off"
            />
          </div>
          <Button
            variant="secondary"
            size="md"
            onClick={addCustomTech}
            disabled={!form.customTech.trim()}
            icon={<Plus className="h-4 w-4" />}
          >
            Add
          </Button>
        </div>

        {/* Selected custom tags */}
        {form.techStack.filter(
          (t) => !Object.values(TECH_STACK).flat().includes(t)
        ).length > 0 && (
          <div className="flex flex-wrap gap-2 mt-3">
            {form.techStack
              .filter((t) => !Object.values(TECH_STACK).flat().includes(t))
              .map((tech) => (
                <span
                  key={tech}
                  className="inline-flex items-center gap-1.5 rounded-full bg-primary text-on-primary border border-primary px-4 py-2 text-sm font-medium"
                >
                  {tech}
                  <button
                    type="button"
                    onClick={() => toggleTech(tech)}
                    className="hover:text-on-primary/70 transition-colors cursor-pointer"
                  >
                    <X className="h-3 w-3" />
                  </button>
                </span>
              ))}
          </div>
        )}
      </div>
    </div>
  );

  const renderStepReview = () => {
    const selectedType = PROJECT_TYPES.find((pt) => pt.id === form.type);

    return (
      <div className="space-y-6">
        <div>
          <h2 className="text-2xl font-display font-bold text-foreground mb-1">
            Review &amp; Generate
          </h2>
          <p className="text-foreground-secondary text-sm">
            Review your project details below. When you&apos;re ready, hit Generate
            to create your documentation.
          </p>
        </div>

        {/* Summary cards */}
        <div className="space-y-4">
          {/* Details */}
          <Card className="space-y-3">
            <div className="flex items-center gap-2 mb-2">
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10">
                <FileText className="h-4 w-4 text-primary" />
              </div>
              <h3 className="text-sm font-semibold text-foreground uppercase tracking-wider">
                Project Details
              </h3>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-2 text-sm">
              <div>
                <span className="text-foreground-secondary">Name:</span>{" "}
                <span className="text-foreground font-medium">{form.name}</span>
              </div>
              {form.ownerName && (
                <div>
                  <span className="text-foreground-secondary">Owner:</span>{" "}
                  <span className="text-foreground font-medium">{form.ownerName}</span>
                </div>
              )}
              {form.teamSize && (
                <div>
                  <span className="text-foreground-secondary">Team Size:</span>{" "}
                  <span className="text-foreground font-medium">{form.teamSize}</span>
                </div>
              )}
            </div>
            <div className="text-sm">
              <span className="text-foreground-secondary">Description:</span>
              <p className="text-foreground mt-1 leading-relaxed whitespace-pre-wrap">
                {form.description}
              </p>
            </div>
          </Card>

          {/* Type */}
          {selectedType && (
            <Card className="flex items-center gap-4">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10">
                <selectedType.icon className="h-5 w-5 text-primary" />
              </div>
              <div>
                <p className="text-xs text-foreground-secondary uppercase tracking-wider font-semibold">
                  Project Type
                </p>
                <p className="text-foreground font-medium">{selectedType.label}</p>
              </div>
            </Card>
          )}

          {/* Tech Stack */}
          {form.techStack.length > 0 && (
            <Card>
              <div className="flex items-center gap-2 mb-3">
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10">
                  <Layers className="h-4 w-4 text-primary" />
                </div>
                <h3 className="text-sm font-semibold text-foreground uppercase tracking-wider">
                  Tech Stack
                </h3>
              </div>
              <div className="flex flex-wrap gap-2">
                {form.techStack.map((tech) => (
                  <span
                    key={tech}
                    className="inline-flex items-center rounded-full bg-primary/10 text-primary border border-primary/20 px-3 py-1 text-xs font-medium"
                  >
                    {tech}
                  </span>
                ))}
              </div>
            </Card>
          )}
        </div>

        {/* Generation overlay */}
        <AnimatePresence>
          {isGenerating && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              className="mt-6"
            >
              <Card glow className="text-center space-y-5 py-10">
                <motion.div
                  animate={{ rotate: 360 }}
                  transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
                  className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10"
                >
                  <Sparkles className="h-7 w-7 text-primary" />
                </motion.div>
                <div>
                  <h3 className="text-lg font-display font-bold text-foreground mb-1">
                    Generating Documentation…
                  </h3>
                  <p className="text-foreground-secondary text-sm">
                    Our AI is crafting your SRS, ERDs, use cases, and more.
                  </p>
                </div>
                {/* Progress bar */}
                <div className="mx-auto w-full max-w-xs">
                  <div className="h-2 rounded-full bg-background-secondary overflow-hidden">
                    <motion.div
                      className="h-full rounded-full bg-primary"
                      initial={{ width: "0%" }}
                      animate={{ width: `${generationProgress}%` }}
                      transition={{ duration: 0.4, ease: "easeOut" }}
                    />
                  </div>
                  <p className="text-xs text-foreground-secondary mt-2 tabular-nums">
                    {generationProgress}%
                  </p>
                </div>
              </Card>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    );
  };

  const stepsRenderers = [
    renderStepDetails,
    renderStepType,
    renderStepTechStack,
    renderStepReview,
  ];

  /* ================================================================ */
  /*  Render                                                           */
  /* ================================================================ */

  return (
    <PageTransition>
      <section className="min-h-screen bg-background-secondary py-12 px-4 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-2xl">
          {/* Header */}
          <FadeIn>
            <div className="mb-8 text-center">
              <Link
                href="/projects"
                className="inline-flex items-center gap-1.5 text-sm text-foreground-secondary hover:text-primary transition-colors mb-6"
              >
                <ArrowLeft className="h-4 w-4" />
                Back to Projects
              </Link>
              <h1 className="text-3xl sm:text-4xl font-display font-bold gradient-text mb-2">
                Create New Project
              </h1>
              <p className="text-foreground-secondary">
                Set up your project and let Velocira generate the documentation.
              </p>
            </div>
          </FadeIn>

          {/* Step Indicator */}
          <StepIndicator current={step} />

          {/* Step Content */}
          <Card className="relative overflow-hidden min-h-[420px]">
            <AnimatePresence mode="wait" custom={direction}>
              <motion.div
                key={step}
                custom={direction}
                variants={slideVariants}
                initial="enter"
                animate="center"
                exit="exit"
                transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
              >
                {stepsRenderers[step]()}
              </motion.div>
            </AnimatePresence>
          </Card>

          {/* Navigation Buttons */}
          <div className="flex items-center justify-between mt-6">
            <div>
              {step > 0 && (
                <Button
                  variant="ghost"
                  size="md"
                  onClick={goBack}
                  disabled={isGenerating}
                  icon={<ArrowLeft className="h-4 w-4" />}
                >
                  Back
                </Button>
              )}
            </div>
            <div className="flex items-center gap-3">
              {step < TOTAL_STEPS - 1 ? (
                <Button
                  variant="primary"
                  size="md"
                  onClick={goNext}
                  icon={<ArrowRight className="h-4 w-4" />}
                >
                  Next
                </Button>
              ) : (
                <Button
                  variant="primary"
                  size="lg"
                  onClick={handleGenerate}
                  loading={isGenerating}
                  disabled={isGenerating}
                  icon={!isGenerating ? <Rocket className="h-4 w-4" /> : undefined}
                >
                  {isGenerating ? "Generating…" : "Create & Generate"}
                </Button>
              )}
            </div>
          </div>

          {/* Step hint */}
          <p className="text-center text-xs text-foreground-secondary mt-4">
            Step {step + 1} of {TOTAL_STEPS}
            {step === 2 && " — Tech stack is optional, skip with Next"}
          </p>
        </div>
      </section>
    </PageTransition>
  );
}
