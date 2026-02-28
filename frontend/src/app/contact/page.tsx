"use client";

import { useState, type FormEvent } from "react";
import {
  Mail,
  MapPin,
  Send,
  Github,
  Twitter,
  Linkedin,
  ChevronDown,
  CheckCircle2,
  Clock,
  ArrowRight,
  Sparkles,
  HelpCircle,
  Navigation,
} from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import Link from "next/link";
import { useLocale } from "@/providers/LocaleProvider";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
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

const contactMethods = [
  {
    icon: Mail,
    label: "Email Us",
    value: "hello@velocira.com",
    description: "We typically respond within 24 hours",
    color: "text-primary",
    bgColor: "bg-primary/10",
  },
  {
    icon: MapPin,
    label: "Our Location",
    value: "Cairo, Egypt",
    description: "The heart of our operations",
    color: "text-accent",
    bgColor: "bg-accent/10",
  },
  {
    icon: Github,
    label: "Social",
    value: "Follow us everywhere",
    description: "GitHub · Twitter · LinkedIn",
    color: "text-info",
    bgColor: "bg-info/10",
  },
];

const subjectOptions = [
  { value: "", label: "Select a topic..." },
  { value: "general", label: "General Inquiry" },
  { value: "bug", label: "Bug Report" },
  { value: "feature", label: "Feature Request" },
  { value: "partnership", label: "Partnership" },
  { value: "other", label: "Other" },
];

const faqItems = [
  {
    question: "How fast is document generation?",
    answer:
      "Velocira generates a complete Software Requirements Specification in under 30 seconds. Our AI pipeline is optimized for speed without sacrificing depth — you get use cases, ERDs, API structures, and more in a single pass.",
  },
  {
    question: "What export formats do you support?",
    answer:
      "We currently support PDF, DOCX, and Markdown exports. Each format is carefully rendered to preserve structure, tables, diagrams, and styling so your documentation looks professional in any context.",
  },
  {
    question: "Is there a free tier?",
    answer:
      "Yes! Our free tier lets you create up to 3 projects with full AI generation capabilities. No credit card required. Upgrade anytime to unlock unlimited projects, team collaboration, and priority support.",
  },
  {
    question: "How secure is my data?",
    answer:
      "Security is foundational to Velocira. All data is encrypted at rest and in transit (AES-256 + TLS 1.3). We use JWT-based authentication with refresh token rotation, and your documents are never used to train AI models.",
  },
  {
    question: "Can I collaborate with my team?",
    answer:
      "Team collaboration is on our roadmap and coming soon. You'll be able to invite team members, assign roles, leave comments on sections, and co-edit documents in real-time. Stay tuned for updates!",
  },
];

/* ------------------------------------------------------------------ */
/*  FAQ Accordion Item                                                 */
/* ------------------------------------------------------------------ */

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
    <div className="border border-border rounded-2xl overflow-hidden transition-colors hover:border-border-hover">
      <button
        onClick={onToggle}
        className="w-full flex items-center justify-between gap-4 p-5 text-start cursor-pointer"
      >
        <span className="text-foreground font-medium">{question}</span>
        <motion.div
          animate={{ rotate: isOpen ? 180 : 0 }}
          transition={{ duration: 0.25, ease: "easeInOut" }}
          className="shrink-0"
        >
          <ChevronDown className="h-5 w-5 text-foreground-secondary" />
        </motion.div>
      </button>
      <AnimatePresence initial={false}>
        {isOpen && (
          <motion.div
            key="content"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
            className="overflow-hidden"
          >
            <div className="px-5 pb-5 text-foreground-secondary leading-relaxed">
              {answer}
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

export default function ContactPage() {
  const { t } = useLocale();

  /* Form state */
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [subject, setSubject] = useState("");
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  /* FAQ state */
  const [openFAQ, setOpenFAQ] = useState<number | null>(null);

  /* Validation */
  const validate = (): boolean => {
    const errs: Record<string, string> = {};
    if (!name.trim()) errs.name = "Name is required";
    if (!email.trim()) errs.email = "Email is required";
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
      errs.email = "Enter a valid email";
    if (!subject) errs.subject = "Please select a subject";
    if (!message.trim()) errs.message = "Message is required";
    else if (message.trim().length < 10)
      errs.message = "Message must be at least 10 characters";
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setSending(true);
    // Simulate network request
    setTimeout(() => {
      setSending(false);
      setSent(true);
      setTimeout(() => {
        setSent(false);
        setName("");
        setEmail("");
        setSubject("");
        setMessage("");
        setErrors({});
      }, 4000);
    }, 1500);
  };

  return (
    <PageTransition>
      {/* ============================================================ */}
      {/*  HERO                                                        */}
      {/* ============================================================ */}
      <section className="relative overflow-hidden py-24 sm:py-32">
        
        

        {/* Geometric accents */}
        <div className="absolute top-24 right-[15%] w-5 h-5 border border-primary/[0.12] rotate-45 hidden sm:block" />
        <div className="absolute bottom-16 left-[12%] w-3 h-3 border border-primary/[0.08] rotate-45 hidden sm:block" />
        <div className="absolute top-1/3 right-[8%] w-16 h-[1px] bg-gradient-to-r from-primary/[0.12] to-transparent hidden lg:block" />

        <div className="relative max-w-4xl mx-auto px-4 text-center">
          <FadeIn>
            <div className="inline-flex items-center gap-2 px-5 py-2 rounded-full bg-primary/[0.06] border border-primary/20 mb-8">
              <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
              <span className="text-sm font-medium text-primary">
                We&apos;d love to hear from you
              </span>
            </div>
            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold font-display">
              <span className="gradient-text">Get in Touch</span>
            </h1>
            <p className="mt-6 text-lg sm:text-xl text-foreground-secondary max-w-2xl mx-auto leading-relaxed">
              Have a question, feedback, or partnership idea? Reach out and
              our team will get back to you as soon as possible.
            </p>
          </FadeIn>
        </div>
      </section>

      {/* ============================================================ */}
      {/*  CONTACT METHODS GRID                                        */}
      {/* ============================================================ */}
      <section className="relative max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 -mt-8">
        <StaggerContainer className="grid sm:grid-cols-3 gap-6">
          {contactMethods.map((method) => (
            <StaggerItem key={method.label}>
              <Card hover className="p-6 text-center h-full">
                <div
                  className={`h-14 w-14 rounded-2xl ${method.bgColor} flex items-center justify-center mx-auto mb-4`}
                >
                  <method.icon className={`h-7 w-7 ${method.color}`} />
                </div>
                <h3 className="text-lg font-semibold text-foreground mb-1">
                  {method.label}
                </h3>
                <p className="text-foreground font-medium">{method.value}</p>
                <p className="text-sm text-foreground-secondary mt-1">
                  {method.description}
                </p>
                {method.label === "Social" && (
                  <div className="flex items-center justify-center gap-3 mt-4">
                    {[
                      { icon: Github, href: "#", label: "GitHub" },
                      { icon: Twitter, href: "#", label: "Twitter" },
                      { icon: Linkedin, href: "#", label: "LinkedIn" },
                    ].map((social) => (
                      <a
                        key={social.label}
                        href={social.href}
                        aria-label={social.label}
                        className="h-9 w-9 rounded-xl bg-primary/10 flex items-center justify-center text-primary hover:bg-primary/20 transition-colors"
                      >
                        <social.icon className="h-4 w-4" />
                      </a>
                    ))}
                  </div>
                )}
              </Card>
            </StaggerItem>
          ))}
        </StaggerContainer>
      </section>

      {/* ============================================================ */}
      {/*  CONTACT FORM + MAP                                          */}
      {/* ============================================================ */}
      <section className="relative max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-24">
        <div className="grid lg:grid-cols-5 gap-10">
          {/* Form — 3 cols */}
          <FadeIn direction="right" className="lg:col-span-3">
            <Card className="p-8">
              <h2 className="text-2xl font-bold font-display text-foreground mb-1">
                Send Us a Message
              </h2>
              <p className="text-foreground-secondary mb-8">
                Fill out the form below and we&apos;ll respond within one
                business day.
              </p>

              <AnimatePresence mode="wait">
                {sent ? (
                  <motion.div
                    key="success"
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.9 }}
                    transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
                    className="text-center py-16"
                  >
                    <motion.div
                      initial={{ scale: 0 }}
                      animate={{ scale: 1 }}
                      transition={{
                        type: "spring",
                        stiffness: 200,
                        damping: 15,
                        delay: 0.1,
                      }}
                      className="h-20 w-20 rounded-full bg-success/10 flex items-center justify-center mx-auto mb-6"
                    >
                      <CheckCircle2 className="h-10 w-10 text-success" />
                    </motion.div>
                    <h3 className="text-2xl font-bold text-foreground mb-2">
                      Message Sent!
                    </h3>
                    <p className="text-foreground-secondary max-w-sm mx-auto">
                      Thank you for reaching out. We&apos;ll get back to you
                      within 24 hours.
                    </p>
                  </motion.div>
                ) : (
                  <motion.form
                    key="form"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    onSubmit={handleSubmit}
                    className="space-y-5"
                  >
                    {/* Name + Email row */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                      <Input
                        label="Name"
                        type="text"
                        placeholder="John Doe"
                        icon={<span className="text-sm">👤</span>}
                        value={name}
                        onChange={(e) => {
                          setName(e.target.value);
                          if (errors.name)
                            setErrors((prev) => {
                              const n = { ...prev };
                              delete n.name;
                              return n;
                            });
                        }}
                        error={errors.name}
                        required
                      />
                      <Input
                        label="Email"
                        type="email"
                        placeholder="you@example.com"
                        icon={<Mail className="h-4 w-4" />}
                        value={email}
                        onChange={(e) => {
                          setEmail(e.target.value);
                          if (errors.email)
                            setErrors((prev) => {
                              const n = { ...prev };
                              delete n.email;
                              return n;
                            });
                        }}
                        error={errors.email}
                        required
                      />
                    </div>

                    {/* Subject dropdown */}
                    <div className="w-full space-y-1.5">
                      <label className="block text-sm font-medium text-foreground-secondary">
                        Subject
                      </label>
                      <select
                        value={subject}
                        onChange={(e) => {
                          setSubject(e.target.value);
                          if (errors.subject)
                            setErrors((prev) => {
                              const n = { ...prev };
                              delete n.subject;
                              return n;
                            });
                        }}
                        required
                        className={`w-full rounded-xl border bg-input-bg px-4 py-3 text-foreground transition-all duration-200 focus:outline-none focus:border-input-focus focus:ring-2 focus:ring-primary/20 cursor-pointer ${
                          errors.subject
                            ? "border-error focus:border-error focus:ring-error/20"
                            : "border-input-border"
                        }`}
                      >
                        {subjectOptions.map((opt) => (
                          <option key={opt.value} value={opt.value}>
                            {opt.label}
                          </option>
                        ))}
                      </select>
                      {errors.subject && (
                        <p className="text-sm text-error mt-1">
                          {errors.subject}
                        </p>
                      )}
                    </div>

                    {/* Message textarea */}
                    <div className="w-full space-y-1.5">
                      <label className="block text-sm font-medium text-foreground-secondary">
                        Message
                      </label>
                      <textarea
                        rows={6}
                        placeholder="Tell us what's on your mind..."
                        value={message}
                        onChange={(e) => {
                          setMessage(e.target.value);
                          if (errors.message)
                            setErrors((prev) => {
                              const n = { ...prev };
                              delete n.message;
                              return n;
                            });
                        }}
                        required
                        className={`w-full rounded-xl border bg-input-bg px-4 py-3 text-foreground placeholder:text-placeholder transition-all duration-200 focus:outline-none focus:border-input-focus focus:ring-2 focus:ring-primary/20 resize-none ${
                          errors.message
                            ? "border-error focus:border-error focus:ring-error/20"
                            : "border-input-border"
                        }`}
                      />
                      {errors.message && (
                        <p className="text-sm text-error mt-1">
                          {errors.message}
                        </p>
                      )}
                    </div>

                    {/* Submit */}
                    <Button
                      type="submit"
                      size="lg"
                      loading={sending}
                      icon={<Send className="h-4 w-4" />}
                      className="w-full sm:w-auto"
                    >
                      {sending ? "Sending..." : "Send Message"}
                    </Button>
                  </motion.form>
                )}
              </AnimatePresence>
            </Card>
          </FadeIn>

          {/* Sidebar — Map + Office Hours — 2 cols */}
          <FadeIn direction="left" className="lg:col-span-2 space-y-6">
            {/* Map placeholder */}
            <Card className="p-0 overflow-hidden">
              <div className="relative h-56 bg-background-secondary flex flex-col items-center justify-center">
                <div className="relative z-10">
                  <div className="h-16 w-16 rounded-full bg-primary/10 flex items-center justify-center">
                    <Navigation className="h-8 w-8 text-primary" />
                  </div>
                </div>
                <p className="relative z-10 mt-4 text-foreground font-semibold text-lg">
                  Cairo, Egypt
                </p>
                <p className="relative z-10 text-sm text-foreground-secondary">
                  30.0444° N, 31.2357° E
                </p>
              </div>
            </Card>

            {/* Office hours */}
            <Card hover className="p-6">
              <div className="flex items-center gap-3 mb-4">
                <div className="h-10 w-10 rounded-xl bg-accent/10 flex items-center justify-center shrink-0">
                  <Clock className="h-5 w-5 text-accent" />
                </div>
                <h3 className="text-lg font-semibold text-foreground">
                  Office Hours
                </h3>
              </div>
              <div className="space-y-3">
                <div className="flex items-center justify-between py-2 border-b border-border">
                  <span className="text-foreground-secondary">
                    Monday — Friday
                  </span>
                  <span className="text-foreground font-medium">
                    9:00 AM — 6:00 PM
                  </span>
                </div>
                <div className="flex items-center justify-between py-2 border-b border-border">
                  <span className="text-foreground-secondary">Timezone</span>
                  <span className="text-foreground font-medium">EET (UTC+2)</span>
                </div>
                <div className="flex items-center justify-between py-2">
                  <span className="text-foreground-secondary">
                    Saturday — Sunday
                  </span>
                  <span className="text-foreground-secondary font-medium italic">
                    Closed
                  </span>
                </div>
              </div>
              <div className="mt-4 p-3 rounded-xl bg-primary/5 border border-primary/10">
                <p className="text-sm text-foreground-secondary">
                  <span className="text-primary font-medium">Tip:</span> For the
                  fastest response, email us during business hours. We aim to
                  reply to all inquiries within 24 hours.
                </p>
              </div>
            </Card>

            {/* Quick social links */}
            <Card hover className="p-6">
              <h3 className="text-lg font-semibold text-foreground mb-4">
                Connect With Us
              </h3>
              <div className="space-y-3">
                {[
                  {
                    icon: Github,
                    label: "GitHub",
                    handle: "@Pyra-Flow",
                    href: "#",
                  },
                  {
                    icon: Twitter,
                    label: "Twitter",
                    handle: "@velocira_ai",
                    href: "#",
                  },
                  {
                    icon: Linkedin,
                    label: "LinkedIn",
                    handle: "Velocira",
                    href: "#",
                  },
                ].map((social) => (
                  <a
                    key={social.label}
                    href={social.href}
                    className="flex items-center gap-3 p-3 rounded-xl hover:bg-card-hover transition-colors group"
                  >
                    <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 group-hover:bg-primary/20 transition-colors">
                      <social.icon className="h-4 w-4 text-primary" />
                    </div>
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-foreground">
                        {social.label}
                      </p>
                      <p className="text-xs text-foreground-secondary truncate">
                        {social.handle}
                      </p>
                    </div>
                    <ArrowRight className="h-4 w-4 text-foreground-secondary ms-auto opacity-0 group-hover:opacity-100 transition-opacity" />
                  </a>
                ))}
              </div>
            </Card>
          </FadeIn>
        </div>
      </section>

      {/* ============================================================ */}
      {/*  FAQ SECTION                                                 */}
      {/* ============================================================ */}
      <section className="relative max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 pb-24">
        <FadeIn>
          <div className="text-center mb-12">
            <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-accent/10 border border-accent/20 mb-6">
              <HelpCircle className="h-4 w-4 text-accent" />
              <span className="text-sm font-medium text-accent">FAQ</span>
            </div>
            <h2 className="text-3xl sm:text-4xl font-bold font-display text-foreground">
              Frequently Asked Questions
            </h2>
            <p className="mt-4 text-foreground-secondary max-w-xl mx-auto">
              Can&apos;t find what you&apos;re looking for? Send us a message
              using the form above and we&apos;ll be happy to help.
            </p>
          </div>
        </FadeIn>

        <FadeIn delay={0.1}>
          <div className="space-y-3">
            {faqItems.map((item, idx) => (
              <FAQItem
                key={idx}
                question={item.question}
                answer={item.answer}
                isOpen={openFAQ === idx}
                onToggle={() => setOpenFAQ(openFAQ === idx ? null : idx)}
              />
            ))}
          </div>
        </FadeIn>
      </section>

      {/* ============================================================ */}
      {/*  CTA                                                         */}
      {/* ============================================================ */}
      <section className="relative overflow-hidden py-24">
        

        <div className="relative max-w-3xl mx-auto px-4 text-center">
          <FadeIn>
            <h2 className="text-3xl sm:text-4xl font-bold font-display text-foreground mb-4">
              Want to Learn More?
            </h2>
            <p className="text-lg text-foreground-secondary max-w-xl mx-auto mb-8">
              Explore what Velocira can do for your documentation workflow, or
              dive straight into the docs.
            </p>
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
              <Link href="/features">
                <Button
                  size="lg"
                  variant="primary"
                  icon={<Sparkles className="h-4 w-4" />}
                >
                  Explore Features
                </Button>
              </Link>
              <Link href="/docs">
                <Button
                  size="lg"
                  variant="outline"
                  icon={<ArrowRight className="h-4 w-4" />}
                >
                  Read the Docs
                </Button>
              </Link>
            </div>
          </FadeIn>
        </div>
      </section>
    </PageTransition>
  );
}
